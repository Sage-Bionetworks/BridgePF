package org.sagebionetworks.bridge.services;

public interface UploadCertificateService {

    /**
     * Creates a CMS key pair for a particular study and save it in permanent storage.
     */
    void createCmsKeyPair(String studyIdentifier);
}
