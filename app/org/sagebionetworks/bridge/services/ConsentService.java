package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;

public interface ConsentService {

    public User consentToResearch(User caller, ConsentSignature researchConsent, Study study, boolean sendEmail);

    public boolean hasUserConsentedToResearch(User caller, Study study);

    public User withdrawConsent(User caller, Study study);

    public void emailConsentAgreement(User caller, Study study);

}
