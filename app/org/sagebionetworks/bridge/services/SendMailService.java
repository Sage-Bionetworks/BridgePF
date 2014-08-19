package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public interface SendMailService {

    public void sendConsentAgreement(User caller, ResearchConsent consent, Study study);
    
}
