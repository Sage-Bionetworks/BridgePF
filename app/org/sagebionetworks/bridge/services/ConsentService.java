package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface ConsentService {

    public UserSession consentToResearch(String sessionToken, ResearchConsent consent, Study study, boolean sendEmail);
    
    public UserSession withdraw(UserSession session, Study study);
    
    public void emailCopy(UserSession session, Study study);
    
}
