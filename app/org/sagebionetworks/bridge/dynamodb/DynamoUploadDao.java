package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.PAGE_SIZE_ERROR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@Component
public class DynamoUploadDao implements UploadDao {
    private DynamoDBMapper mapper;
    private DynamoIndexHelper healthCodeRequestedOnIndex;

    private static final String UPLOAD_ID = "uploadId";
    private static final String STUDY_ID = "studyId";
    private static final String REQUESTED_ON = "requestedOn";
    private static final String HEALTH_CODE = "healthCode";
    private static final String STUDY_ID_REQUESTED_ON_INDEX = "studyId-requestedOn-index";
    
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
    public ForwardCursorPagedResourceList<Upload> getUploads(String healthCode, DateTime startTime, DateTime endTime, int pageSize,
            String offsetKey) {
        // Set a sane upper limit on this.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        
        RangeKeyCondition condition = new RangeKeyCondition(REQUESTED_ON).between(
                startTime.getMillis(), endTime.getMillis());
        
        Index index = healthCodeRequestedOnIndex.getIndex();
        List<Upload> results = new ArrayList<>(pageSize);

        QuerySpec spec = new QuerySpec()
                .withHashKey(HEALTH_CODE, healthCode)
                .withMaxPageSize(pageSize)
                .withRangeKeyCondition(condition); // this is not a filter, it should not require paging on our side.
        if (offsetKey != null) {
            spec.withExclusiveStartKey(new KeyAttribute(UPLOAD_ID, offsetKey));
        }
        ItemCollection<QueryOutcome> query = index.query(spec);
        
        query.forEach((item) -> {
            DynamoUpload2 oneRecord = BridgeObjectMapper.get().convertValue(item.asMap(), DynamoUpload2.class);
            results.add(oneRecord);
        });

        Map<String,AttributeValue> key = query.getLastLowLevelResult().getQueryResult().getLastEvaluatedKey();
        String nextOffsetKey = (key != null) ? key.get(UPLOAD_ID).getS() : null;

        return new ForwardCursorPagedResourceList<>(results, nextOffsetKey, pageSize)
                .withFilter("startTime", startTime.toString())
                .withFilter("endTime", endTime.toString());
    }

    /** {@inheritDoc} */
    @Override
    public ForwardCursorPagedResourceList<Upload> getStudyUploads(StudyIdentifier studyId, DateTime startTime,
            DateTime endTime, int pageSize, String offsetKey) {
        checkNotNull(studyId);

        // Just set a sane upper limit on this.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }

        // only query one page each time client calling this method
        QueryResultPage<DynamoUpload2> page = mapper.queryPage(DynamoUpload2.class,
                createGetQuery(studyId, startTime, endTime, offsetKey, pageSize));

        Map<String, List<Object>> resultMap = mapper.batchLoad(page.getResults());
        List<Upload> uploadList = new ArrayList<>();
        for (List<Object> resultList : resultMap.values()) {
            for (Object oneResult : resultList) {
                if (!DynamoUpload2.class.isInstance(oneResult)) {
                    // This should never happen, but just in case.
                    throw new BridgeServiceException(String.format(
                            "DynamoDB returned objects of type %s instead of %s",
                            oneResult.getClass().getName(), DynamoUpload2.class.getName()));
                }

                uploadList.add((DynamoUpload2) oneResult);
            }
        }

        String nextPageOffsetKey = (page.getLastEvaluatedKey() != null) ? page.getLastEvaluatedKey().get(UPLOAD_ID).getS() : null;
        
        return new ForwardCursorPagedResourceList<>(uploadList, nextPageOffsetKey, pageSize)
                .withFilter("startDate", startTime.toString())
                .withFilter("endDate", endTime.toString());
    }

    private DynamoDBQueryExpression<DynamoUpload2> createGetQuery(StudyIdentifier studyId, DateTime startTime, DateTime endTime,
                                                                             String offsetKey, int pageSize) {

        DynamoDBQueryExpression<DynamoUpload2> query = createCountQuery(studyId.getIdentifier(), startTime, endTime);
        if (offsetKey != null) {
            // load table again to get the one last evaluated upload
            DynamoUpload2 retLastEvaluatedUpload = mapper.load(DynamoUpload2.class, offsetKey);

            Map<String,AttributeValue> map = new HashMap<>();
            map.put(UPLOAD_ID, new AttributeValue().withS(offsetKey));
            map.put(REQUESTED_ON, new AttributeValue().withN(String.valueOf(retLastEvaluatedUpload.getRequestedOn())));
            map.put(STUDY_ID, new AttributeValue().withS(studyId.getIdentifier()));
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
        query.withIndexName(STUDY_ID_REQUESTED_ON_INDEX);
        query.withHashKeyValues(upload);
        query.withConsistentRead(false);
        query.setScanIndexForward(false);
        query.withRangeKeyCondition(REQUESTED_ON, rangeKeyCondition);
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
                DynamoUpload2.class, HEALTH_CODE, healthCode, null);
        
        if (!uploadsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(uploadsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}

