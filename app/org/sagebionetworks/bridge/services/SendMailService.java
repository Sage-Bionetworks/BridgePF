package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

public interface SendMailService {

    void sendConsentAgreement(User caller, ConsentSignature consent, StudyConsent studyConsent,
            SharingScope sharingScope);

    void sendStudyParticipantsRoster(Study study, List<StudyParticipant> participants);
}
