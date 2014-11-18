package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;

public interface UploadService {

    /**
     * Creates a upload session. Returns a presigned URL for the client
     * to use for upload.
     */
    UploadSession createUpload(User user, UploadRequest uploadRequest);

    /**
     * Client calls back when the upload is complete.
     *
     * @param id Upload ID
     */
    void uploadComplete(String id);
}
