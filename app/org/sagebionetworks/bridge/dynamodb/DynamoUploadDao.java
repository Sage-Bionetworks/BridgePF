package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.PAGE_SIZE_ERROR;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@Component
public class DynamoUploadDao implements UploadDao {
    private DynamoDBMapper mapper;
    private DynamoIndexHelper healthCodeRequestedOnIndex;
    private HealthCodeDao healthCodeDao;

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
    
    @Autowired
    final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
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
            // Very old uploads (2+ years ago) did not have studyId set; for these we must do 
            // a lookup in the legacy DynamoHealthCode table.
            if (upload.getStudyId() == null) { 
                String studyId = healthCodeDao.getStudyIdentifier(upload.getHealthCode());
                if (studyId == null) {
                    throw new EntityNotFoundException(DynamoStudy.class,
                            "Study not found for upload. User may have been deleted from system.");
                }
                upload.setStudyId(studyId);
            }
            return upload;
        }
        throw new NotFoundException(String.format("Upload ID %s not found", uploadId));
    }
    
    /** {@inheritDoc} */
    @Override
    public ForwardCursorPagedResourceList<Upload> getUploads(String healthCode, DateTime startTime, DateTime endTime,
            int pageSize, String offsetKey) {
        // Set a sane upper limit on this.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        int sizeWithIndicatorRecord = pageSize+1;
        long offsetTimestamp = (offsetKey == null) ? 0 : Long.parseLong(offsetKey);
        long offsetStartTime = Math.max(startTime.getMillis(), offsetTimestamp);
        
        RangeKeyCondition condition = new RangeKeyCondition(REQUESTED_ON).between(
                offsetStartTime, endTime.getMillis());
        
        QuerySpec spec = new QuerySpec()
                .withHashKey(HEALTH_CODE, healthCode)
                .withMaxPageSize(sizeWithIndicatorRecord)
                .withRangeKeyCondition(condition); // this is not a filter, it should not require paging on our side.
        QueryOutcome outcome = healthCodeRequestedOnIndex.query(spec);
        
        List<Upload> itemsToLoad = new ArrayList<>(sizeWithIndicatorRecord);
        Iterator<Item> iter = outcome.getItems().iterator();
        while (iter.hasNext() && itemsToLoad.size() < sizeWithIndicatorRecord) {
            Item item = iter.next();
            DynamoUpload2 indexKeys = BridgeObjectMapper.get().convertValue(item.asMap(), DynamoUpload2.class);
            itemsToLoad.add(indexKeys); 
        }
        
        List<Upload> results = new ArrayList<>(sizeWithIndicatorRecord);
        Map<String, List<Object>> resultMap = mapper.batchLoad(itemsToLoad);
        for (List<Object> resultList : resultMap.values()) {
            for (Object oneResult : resultList) {
                results.add((Upload) oneResult);
            }
        }
        
        // Due to the return in a map, these items are not in order by requestedOn attribute, so sort them.
        results.sort(Comparator.comparing(Upload::getRequestedOn));
        
        String nextOffsetKey = null;
        if (results.size() > pageSize) {
            nextOffsetKey = Long.toString(Iterables.getLast(results).getRequestedOn());
        }

        int lastIndex = Math.min(pageSize, results.size());
        return new ForwardCursorPagedResourceList<>(results.subList(0, lastIndex), nextOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
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
        
        return new ForwardCursorPagedResourceList<>(uploadList, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
    }

    private DynamoDBQueryExpression<DynamoUpload2> createGetQuery(StudyIdentifier studyId, DateTime startTime, DateTime endTime,
                                                                             String offsetKey, int pageSize) {

        DynamoDBQueryExpression<DynamoUpload2> query = createCountQuery(studyId.getIdentifier(), startTime, endTime);
        if (offsetKey != null) {
            // load table again to get the one last evaluated upload
            DynamoUpload2 retLastEvaluatedUpload = mapper.load(DynamoUpload2.class, offsetKey);
            if (retLastEvaluatedUpload == null) {
                throw new BadRequestException("Invalid offsetKey: " + offsetKey);
            }
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

