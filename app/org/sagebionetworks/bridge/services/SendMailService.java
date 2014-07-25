package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;

public interface SendMailService {

    public void sendConsentAgreement(String recipientEmail, ResearchConsent consent, Study study);
    
}
