package org.sagebionetworks.bridge.services;

import java.util.Collections;

import java.util.Comparator;
import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.stormpath.StormpathAccountIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;

public class ParticipantRosterGenerator implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ParticipantRosterGenerator.class);

    private static final Comparator<StudyParticipant> STUDY_PARTICIPANT_COMPARATOR = new Comparator<StudyParticipant>() {
        @Override
        public int compare(StudyParticipant p1, StudyParticipant p2) {
            return p1.getEmail().compareTo(p2.getEmail());
        }
    };
    
    private final Application app;
    
    private final Study study;
    
    private final SendMailService sendMailService;
    
    private final UserProfileService userProfileService;
    
    public ParticipantRosterGenerator(Application app, Study study, UserProfileService userProfileService, SendMailService sendMailService) {
        this.app = app;
        this.study = study;
        this.userProfileService = userProfileService;
        this.sendMailService = sendMailService;
    }
    
    @Override
    public void run() {
        try {
            List<StudyParticipant> participants = Lists.newArrayList();

            // We must iterate over every account in the application, not just the study directory, in case 
            // a user signed up with a different study. There is no way to search/query for specific custom 
            // data.
            String consentKey = study.getIdentifier() + BridgeConstants.CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX; 
            
            StormpathAccountIterator iterator = new StormpathAccountIterator(app);
            for (List<Account> page : iterator) {
                for (Account account : page) {
                    if (account.getCustomData().containsKey(consentKey)) {
                        UserProfile profile = userProfileService.profileFromAccount(account);
                        StudyParticipant p = new StudyParticipant();
                        p.setFirstName(profile.getFirstName());
                        p.setLastName(profile.getLastName());
                        p.setEmail(profile.getEmail());
                        p.setPhone(profile.getPhone());
                        participants.add(p);
                    }
                }
            }
            Collections.sort(participants, STUDY_PARTICIPANT_COMPARATOR);
            sendMailService.sendStudyParticipantsRoster(study, participants);
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
        }
    }        

}
