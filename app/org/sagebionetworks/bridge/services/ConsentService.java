package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface ConsentService {

    public UserSession consentToResearch(String sessionToken, ResearchConsent researchConsent, Study study, boolean sendEmail);
    
    public UserSession withdrawConsent(String sessionToken, Study study);
    
    public void emailConsentAgreement(String sessionToken, Study study);
    
}
