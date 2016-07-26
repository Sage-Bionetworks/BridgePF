package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;
import java.util.List;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public interface UploadDao {
    /**
     * Creates a new upload.
     *
     * @param uploadRequest
     *         upload request from user
     * @param studyId
     *         the study of the user
     * @param healthCode
     *         user's health code
     * @return upload metadata of created upload
     */
    Upload createUpload(@Nonnull UploadRequest uploadRequest, @Nonnull StudyIdentifier studyId, @Nonnull String healthCode);

    /**
     * Gets the upload metadata associated with this upload.
     *
     * @param uploadId
     *         upload ID to retrieve
     * @return upload metadata
     */
    Upload getUpload(@Nonnull String uploadId);
    
    /**
     * Get the uploads for an indicated time range.
     */
    List<? extends Upload> getUploads(@Nonnull String healthCode, @Nonnull DateTime startTime, @Nonnull DateTime endTime);

    /**
     * Signals to the Bridge server that the file has been uploaded. This also kicks off upload validation.
     *
     * @param completedBy
     *         a string description of the client that is completing this upload (client, s3 listener, etc.)
     * @param upload
     *         upload to mark as completed
     */
    void uploadComplete(@Nonnull UploadCompletionClient completedBy, @Nonnull Upload upload);

    /**
     * Persists the validation status, message list, and health data record ID (if it exists) to the Upload metadata
     * object.
     *
     * @param upload
     *         Upload metadata object to write to, must be non-null
     * @param status
     *         upload status, generally VALIDATION_FAILED or SUCCEEDED, must be non-null
     * @param validationMessageList
     *         validation messages, generally used for error message, must be non-null and
     *         non-empty, and must not contain null elements
     * @param recordId
     *         ID of the corresponding health data record, may be null if the record doesn't exist
     */
    void writeValidationStatus(@Nonnull Upload upload, @Nonnull UploadStatus status,
            @Nonnull List<String> validationMessageList, String recordId);
}
