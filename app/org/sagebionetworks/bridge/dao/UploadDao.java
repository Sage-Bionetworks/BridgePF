package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.upload.UploadRequest;

public interface UploadDao {

    /**
     * Creates a new upload.
     *
     * @return Upload ID
     */
    String createUpload(UploadRequest upload, String healthCode);

    /**
     * Gets the object associated with this upload.
     */
    String getObjectId(String uploadId);

    /**
     * Marks the specified upload as complete.
     *
     * @param uploadId
     */
    void uploadComplete(String uploadId);

    /**
     * Is the upload complete?
     */
    boolean isComplete(String uploadId);
}
