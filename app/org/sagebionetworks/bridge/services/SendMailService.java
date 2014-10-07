package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

public interface SendMailService {

    public void sendConsentAgreement(User caller, ConsentSignature consent, StudyConsent studyConsent);

}
