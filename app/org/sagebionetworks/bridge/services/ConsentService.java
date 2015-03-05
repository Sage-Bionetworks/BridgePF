package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ConsentService {

    ConsentSignature getConsentSignature(Study study, User user);

    User consentToResearch(Study study, User user, ConsentSignature consentSignature,
            SharingScope sharingScope, boolean sendEmail);

    boolean hasUserSignedMostRecentConsent(StudyIdentifier studyIdentifier, User user);

    boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user);

    void withdrawConsent(Study study, User user);

    void emailConsentAgreement(Study study, User user);

    boolean isStudyAtEnrollmentLimit(Study study);

    void incrementStudyEnrollment(Study study) throws StudyLimitExceededException;

    void decrementStudyEnrollment(Study study);
}
