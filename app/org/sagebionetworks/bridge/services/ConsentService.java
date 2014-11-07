package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public interface ConsentService {

    public User consentToResearch(User caller, ConsentSignature researchConsent, Study study, boolean sendEmail);

    public boolean hasUserConsentedToResearch(User caller, Study study);

    public User withdrawConsent(User caller, Study study);

    public void emailConsentAgreement(User caller, Study study);

}
