package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;

public interface ConsentService {

    public ConsentSignature getConsentSignature(User caller, Study study);

    public User consentToResearch(User caller, ConsentSignature consentSignature, Study study, boolean sendEmail);

    public boolean hasUserConsentedToResearch(User caller, Study study);

    public User withdrawConsent(User caller, Study study);

    public void emailConsentAgreement(User caller, Study study);

    public boolean isStudyAtEnrollmentLimit(Study study);
    
    public void incrementStudyEnrollment(Study study) throws StudyLimitExceededException;
    
    public void decrementStudyEnrollment(Study study);
}
