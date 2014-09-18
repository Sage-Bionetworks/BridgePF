package org.sagebionetworks.bridge.services;

import java.net.URL;

public interface UploadService {

    /**
     * Creates a upload session. Returns a presigned URL for the client
     * to use for upload.
     */
    URL createUpload();

    /**
     * Client calls back when the upload is complete.
     *
     * @param id Upload ID
     */
    void uploadComplete(String id);
}
