package org.sagebionetworks.bridge.services;

import java.util.Collections;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
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
    
    public ParticipantRosterGenerator(Iterator<Account> accounts, Study study, SendMailService sendMailService) {
        this.accounts = accounts;
        this.study = study;
        this.sendMailService = sendMailService;
    }
    
    @Override
    public void run() {
        try {
            List<StudyParticipant> participants = Lists.newArrayList();
            while(accounts.hasNext()) {
                Account account = accounts.next();
                if (account.getConsentSignature() != null) {
                    StudyParticipant p = new StudyParticipant();
                    p.setFirstName(account.getFirstName());
                    p.setLastName(account.getLastName());
                    p.setEmail(account.getEmail());
                    p.setPhone(account.getPhone());
                    for (String attribute : study.getUserProfileAttributes()) {
                        String value = account.getAttribute(attribute);
                        // Whether present or not, add an entry.
                        p.put(attribute, value);
                    }
                    participants.add(p);
                }
            }
            Collections.sort(participants, STUDY_PARTICIPANT_COMPARATOR);
            sendMailService.sendStudyParticipantsRoster(study, participants);
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
        }
    }        

}
