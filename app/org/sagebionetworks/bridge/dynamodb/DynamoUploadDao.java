package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.PAGE_SIZE_ERROR;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@Component
public class DynamoUploadDao implements UploadDao {
    private DynamoDBMapper mapper;
    private DynamoIndexHelper healthCodeRequestedOnIndex;
    private DynamoIndexHelper studyIdRequestedOnIndex;

    private static final String UPLOAD_ID = "uploadId";
    private static final String STUDY_ID = "studyId";
    
    /**
     * This is the DynamoDB mapper that reads from and writes to our DynamoDB table. This is normally configured by
     * Spring.
     */
    @Resource(name = "uploadDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * DynamoDB Index reference for the healthCode-requestedOn index. 
     */
    @Resource(name = "uploadHealthCodeRequestedOnIndex")
    final void setHealthCodeRequestedOnIndex(DynamoIndexHelper healthCodeRequestedOnIndex) {
        this.healthCodeRequestedOnIndex = healthCodeRequestedOnIndex;
    }
    
    /**
     * DynamoDB Index reference for the studyId-requestedOn index. 
     */
    @Resource(name = "uploadStudyIdRequestedOnIndex")
    final void setStudyIdRequestedOnIndex(DynamoIndexHelper studyIdRequestedOnIndex) {
        this.studyIdRequestedOnIndex = studyIdRequestedOnIndex;
    }
    
    /** {@inheritDoc} */
    @Override
    public Upload createUpload(@Nonnull UploadRequest uploadRequest, @Nonnull StudyIdentifier studyId,
            @Nonnull String healthCode, @Nullable String originalUploadId) {
        checkNotNull(uploadRequest, "Upload request is null");
        checkNotNull(studyId, "Study identifier is null");
        checkArgument(StringUtils.isNotBlank(healthCode), "Health code is null or blank");        

        // Always write new uploads to the new upload table.
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, healthCode);
        upload.setStudyId(studyId.getIdentifier());
        upload.setRequestedOn(DateUtils.getCurrentMillisFromEpoch());

        if (originalUploadId != null) {
            // This is a dupe. Tag it as such.
            upload.setDuplicateUploadId(originalUploadId);
            upload.setStatus(UploadStatus.DUPLICATE);
        }

        mapper.save(upload);
        return upload;
    }

    // TODO: Cache this, or make it so that calling getUpload() and uploadComplete() in sequence don't cause duplicate
    // calls to DynamoDB.
    /** {@inheritDoc} */
    @Override
    public Upload getUpload(@Nonnull String uploadId) {
        // Fetch upload from DynamoUpload2
        DynamoUpload2 key = new DynamoUpload2();
        key.setUploadId(uploadId);
        DynamoUpload2 upload = mapper.load(key);
        if (upload != null) {
            return upload;
        }

        throw new NotFoundException(String.format("Upload ID %s not found", uploadId));
    }
    
    /** {@inheritDoc} */
    @Override
    public List<? extends Upload> getUploads(String healthCode, DateTime startTime, DateTime endTime) {
        RangeKeyCondition condition = new RangeKeyCondition("requestedOn").between(
                startTime.getMillis(), endTime.getMillis());
        
        return healthCodeRequestedOnIndex.query(DynamoUpload2.class, "healthCode", healthCode, condition);
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<? extends Upload> getStudyUploads(StudyIdentifier studyId, DateTime startTime, DateTime endTime, int pageSize, String offsetKey) {
        checkNotNull(studyId);

        // Just set a sane upper limit on this.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }

        // only query one page each time client calling this method
        QueryResultPage<DynamoUpload2> page = mapper.queryPage(DynamoUpload2.class,
                createGetQuery(studyId, startTime, endTime, offsetKey, pageSize));

        String nextPageOffsetKey = (page.getLastEvaluatedKey() != null) ? page.getLastEvaluatedKey().get(UPLOAD_ID).getS() : null;

        int total = mapper.count(DynamoUpload2.class, createCountQuery(studyId.getIdentifier(), startTime, endTime));

        PagedResourceList<DynamoUpload2> resourceList = new PagedResourceList<>(page.getResults(), null, pageSize, total)
                .withOffsetKey(nextPageOffsetKey);

        return resourceList;
    }

    private DynamoDBQueryExpression<DynamoUpload2> createGetQuery(StudyIdentifier studyId, DateTime startTime, DateTime endTime,
                                                                             String offsetKey, int pageSize) {

        DynamoDBQueryExpression<DynamoUpload2> query = createCountQuery(studyId.getIdentifier(), startTime, endTime);
        if (offsetKey != null) {
            Map<String,AttributeValue> map = new HashMap<>();
            map.put(STUDY_ID, new AttributeValue().withS(studyId.getIdentifier()));
            map.put(UPLOAD_ID, new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }
        query.withLimit(pageSize);
        return query;
    }

    /**
     * Create a query for records applying the filter values if they exist.
     */
    private DynamoDBQueryExpression<DynamoUpload2> createCountQuery(String studyId, DateTime startTime, DateTime endTime) {
        Condition rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(startTime.getMillis())),
                        new AttributeValue().withN(String.valueOf(endTime.getMillis())));

        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId(studyId);

        DynamoDBQueryExpression<DynamoUpload2> query = new DynamoDBQueryExpression<>();
        query.withIndexName("studyId-requestedOn-index");
        query.withHashKeyValues(upload);
        query.withConsistentRead(false);
        query.setScanIndexForward(false);
        query.withRangeKeyCondition("requestedOn", rangeKeyCondition);
        return query;
    }

    /** {@inheritDoc} */
    @Override
    public void uploadComplete(@Nonnull UploadCompletionClient completedBy, @Nonnull Upload upload) {
        DynamoUpload2 upload2 = (DynamoUpload2) upload;

        upload2.setStatus(UploadStatus.VALIDATION_IN_PROGRESS);

        // TODO: If we globalize Bridge, we'll need to make this timezone configurable.
        upload2.setUploadDate(LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE));
        upload2.setCompletedOn(DateUtils.getCurrentMillisFromEpoch());
        upload2.setCompletedBy(completedBy);
        try {
            mapper.save(upload2);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException("Upload " + upload.getUploadId() + " is already complete");
        }
    }

    /**
     * Writes validation status and appends messages to Dynamo DB. Only DynamoUpload2 objects can have status and
     * validation. DynamoUpload objects will be ignored.
     *
     * @see org.sagebionetworks.bridge.dao.UploadDao#writeValidationStatus
     */
    @Override
    public void writeValidationStatus(@Nonnull Upload upload, @Nonnull UploadStatus status,
            @Nonnull List<String> validationMessageList, String recordId) {
        // set status and append messages
        DynamoUpload2 upload2 = (DynamoUpload2) upload;
        upload2.setStatus(status);
        upload2.appendValidationMessages(validationMessageList);
        upload2.setRecordId(recordId);

        // persist
        mapper.save(upload2);
    }
    
    @Override
    public void deleteUploadsForHealthCode(@Nonnull String healthCode) {
        List<? extends Upload> uploadsToDelete = healthCodeRequestedOnIndex.queryKeys(
                DynamoUpload2.class, "healthCode", healthCode, null);
        
        if (!uploadsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(uploadsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}

