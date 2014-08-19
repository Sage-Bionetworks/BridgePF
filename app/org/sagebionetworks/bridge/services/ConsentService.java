package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public interface ConsentService {

    public User consentToResearch(User caller, ResearchConsent researchConsent, Study study, boolean sendEmail);
    
    public boolean hasUserConsentedToResearch(User caller, Study study);
    
    // TODO: Remove. This is currently equivalent to deleting a user.
    public void withdrawConsent(User caller, Study study);
    
    public void emailConsentAgreement(User caller, Study study);
    
    public User suspendDataSharing(User caller, Study study);
    
    public User resumeDataSharing(User caller, Study study);
    
}
