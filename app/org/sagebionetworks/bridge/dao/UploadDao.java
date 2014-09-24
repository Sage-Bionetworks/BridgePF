package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.UploadRequest;

public interface UploadDao {

    /**
     * Creates a new upload.
     *
     * @return Upload ID
     */
    String createUpload(UploadRequest upload, String healthCode);

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
