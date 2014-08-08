package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface ConsentService {

    void consentToResearch(String sessionToken, ResearchConsent consent, Study study);
    
    void withdraw(UserSession session, Study study);
    
    void emailCopy(UserSession session, Study study);
    
}
