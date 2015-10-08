package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface UploadCertificateService {

    /**
     * Creates a CMS key pair for a particular study and save it in permanent storage.
     */
    void createCmsKeyPair(StudyIdentifier studyIdentifier);
    
    /**
     * Get the PEM file for the public key of the CMS key pair. Study developers need 
     * access to this certificate to encrypt data they send to us.
     * 
     * @param studyIdentifier
     * @return
     */
    String getPublicKeyAsPem(StudyIdentifier studyIdentifier);
    
}
