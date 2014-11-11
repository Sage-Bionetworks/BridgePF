package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

public interface SendMailService {

    public void sendConsentAgreement(User caller, ConsentSignature consent, StudyConsent studyConsent);

}
