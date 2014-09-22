package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.UploadRequest;
import org.sagebionetworks.bridge.models.UploadSession;
import org.sagebionetworks.bridge.models.User;

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
