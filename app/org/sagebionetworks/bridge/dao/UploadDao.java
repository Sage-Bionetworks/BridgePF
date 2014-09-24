package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.UploadRequest;

public interface UploadDao {

    /**
     * @return Upload ID
     */
    String createUpload(UploadRequest upload, String healthCode);

    void uploadComplete(String uploadId);
}
