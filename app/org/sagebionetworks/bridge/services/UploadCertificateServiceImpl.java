package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class UploadCertificateServiceImpl {

    private static final BridgeConfig config = BridgeConfigFactory.getConfig();

    public void createCertificate(String studyIdentifier) {
        String fqdn = config.getStudyHostname(studyIdentifier);
    }
}
