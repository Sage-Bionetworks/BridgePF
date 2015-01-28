package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;

import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;

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
}
