package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;
import java.util.List;

import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public interface UploadDao {
    /**
     * Creates a new upload.
     *
     * @param uploadRequest
     *         upload request from user
     * @param healthCode
     *         user's health code
     * @return upload metadata of created upload
     */
    Upload createUpload(@Nonnull UploadRequest uploadRequest, @Nonnull String healthCode);

    /**
     * <p>
     * Gets the failed uploads between the specified dates, inclusive, in YYYY-MM-DD format. A failed upload is any
     * upload with status VALIDATION_FAILED or VALIDATION_IN_PROGRESS (validation crashing  during validation). This is
     * generally useful for redriving uploads that have failed validation.
     * </p>
     * <p>
     * Note that this method should not be called with an endDate on or after the current date, as this may incorrectly
     * return uploads that are still validating.
     * </p>
     * <p>
     * Also note that this method doesn't catch uploads that failed to upload to S3.
     * </p>
     *
     * @param startDate
     *         start date, inclusive, in YYYY-MM-DD format
     * @param endDate
     *         end date, inclusive, in YYYY-MM-DD format
     * @return list of failed uploads
     */
    List<? extends Upload> getFailedUploadsForDates(@Nonnull String startDate, @Nonnull String endDate);

    /**
     * Gets the upload metadata associated with this upload.
     *
     * @param uploadId
     *         upload ID to retrieve
     * @return upload metadata
     */
    Upload getUpload(@Nonnull String uploadId);

    /**
     * Signals to the Bridge server that the file has been uploaded. This also kicks off upload validation.
     *
     * @param upload
     *         upload to mark as completed
     */
    void uploadComplete(@Nonnull Upload upload);

    /**
     * Persists the validation status and message list to the Upload metadata object.
     *
     * @param upload
     *         Upload metadata object to write to, must be non-null
     * @param status
     *         upload status, generally VALIDATION_FAILED or SUCCEEDED, must be non-null
     * @param validationMessageList
     *         validation messages, generally used for error message, must be non-null and
     *         non-empty, and must not contain null elements
     */
    void writeValidationStatus(@Nonnull Upload upload, @Nonnull UploadStatus status,
            @Nonnull List<String> validationMessageList);
}
