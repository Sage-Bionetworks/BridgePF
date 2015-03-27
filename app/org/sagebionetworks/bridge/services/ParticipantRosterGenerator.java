package org.sagebionetworks.bridge.services;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.ParticipantRosterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class ParticipantRosterGenerator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantRosterGenerator.class);

    private static final Comparator<StudyParticipant> STUDY_PARTICIPANT_COMPARATOR = new Comparator<StudyParticipant>() {
        @Override
        public int compare(StudyParticipant p1, StudyParticipant p2) {
            return p1.getEmail().compareTo(p2.getEmail());
        }
    };

    private final Study study;

    private final Iterator<Account> accounts;

    private final SendMailService sendMailService;
    
    private final HealthCodeService healthCodeService;
    
    private final ParticipantOptionsService optionsService;

    public ParticipantRosterGenerator(Iterator<Account> accounts, Study study, SendMailService sendMailService,
                    HealthCodeService healthCodeService, ParticipantOptionsService optionsService) {
        this.accounts = accounts;
        this.study = study;
        this.sendMailService = sendMailService;
        this.healthCodeService = healthCodeService;
        this.optionsService = optionsService;
    }

    @Override
    public void run() {
        try {
            OptionLookup sharingLookup = optionsService.getOptionForAllStudyParticipants(
                study, ParticipantOption.SHARING_SCOPE);

            List<StudyParticipant> participants = Lists.newArrayList();
            while (accounts.hasNext()) {
                Account account = accounts.next();
                if (account.getConsentSignature() != null) {
                    
                    String healthCode = healthCodeService.getMapping(account.getHealthId()).getCode();
                    SharingScope sharing = sharingLookup.getSharingScope(healthCode);

                    StudyParticipant participant = new StudyParticipant();
                    participant.setFirstName(account.getFirstName());
                    participant.setLastName(account.getLastName());
                    participant.setEmail(account.getEmail());
                    participant.setSharingScope(sharing);
                    for (String attribute : study.getUserProfileAttributes()) {
                        String value = account.getAttribute(attribute);
                        // Whether present or not, add an entry.
                        participant.put(attribute, value);
                    }
                    participants.add(participant);
                }
            }
            Collections.sort(participants, STUDY_PARTICIPANT_COMPARATOR);

            MimeTypeEmailProvider roster = new ParticipantRosterProvider(study, participants);
            sendMailService.sendEmail(roster);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

}
