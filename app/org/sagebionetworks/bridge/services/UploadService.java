package org.sagebionetworks.bridge.services;

import java.net.URL;

public interface UploadService {

    URL createUpload();

    void uploadComplete(String id);
}
