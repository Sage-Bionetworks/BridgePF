package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ConsentService {

    public ConsentSignature getConsentSignature(Study study, User user);

    public User consentToResearch(Study study, User user, ConsentSignature consentSignature, boolean sendEmail);

    public boolean hasUserSignedMostRecentConsent(StudyIdentifier studyIdentifier, User user);

    public boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user);

    public void withdrawConsent(Study study, User user);

    public void emailConsentAgreement(Study study, User user);

    public boolean isStudyAtEnrollmentLimit(Study study);

    public void incrementStudyEnrollment(Study study) throws StudyLimitExceededException;

    public void decrementStudyEnrollment(Study study);
}
