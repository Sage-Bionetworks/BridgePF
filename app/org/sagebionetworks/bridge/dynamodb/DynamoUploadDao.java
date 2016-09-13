package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
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
        upload.setDuplicateUploadId(originalUploadId);
        upload.setStudyId(studyId.getIdentifier());
        upload.setRequestedOn(DateUtils.getCurrentMillisFromEpoch());
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
    public List<? extends Upload> getStudyUploads(StudyIdentifier studyId, DateTime startTime, DateTime endTime) {
        RangeKeyCondition condition = new RangeKeyCondition("requestedOn").between(
                startTime.getMillis(), endTime.getMillis());
        
        return studyIdRequestedOnIndex.query(DynamoUpload2.class, "studyId", studyId.getIdentifier(), condition);
    }
    
    /** {@inheritDoc} */
    @Override
    public void uploadComplete(@Nonnull UploadCompletionClient completedBy, @Nonnull UploadStatus status,
            @Nonnull Upload upload) {
        DynamoUpload2 upload2 = (DynamoUpload2) upload;

        upload2.setStatus(status);

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
}
