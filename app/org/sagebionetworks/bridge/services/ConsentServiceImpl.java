package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;

public class ConsentServiceImpl implements ConsentService, ApplicationEventPublisherAware {

    private Client stormpathClient;
    private AccountEncryptionService accountEncryptionService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;
    private ApplicationEventPublisher publisher;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public ConsentSignature getConsentSignature(final User caller, final Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        final StudyConsent consent = studyConsentDao.getConsent(study.getIdentifier());
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthCode(), consent);
        return consentSignature;
    }

    @Override
    public User consentToResearch(final User caller, final ConsentSignature consentSignature, 
        final Study study, final boolean sendEmail) throws BridgeServiceException {
        
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        if (caller.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        // Stormpath account
        final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
        HealthId hid = accountEncryptionService.getHealthCode(study, account);
        if (hid == null) {
            hid = accountEncryptionService.createAndSaveHealthCode(study, account);
        }
        
        final HealthId healthId = hid;
        // Give consent
        final StudyConsent studyConsent = studyConsentDao.getConsent(study.getIdentifier());
        userConsentDao.giveConsent(healthId.getCode(), studyConsent, consentSignature);
        // Publish event
        publisher.publishEvent(new UserEnrolledEvent(caller, study));
        // Sent email
        if (sendEmail) {
            sendMailService.sendConsentAgreement(caller, consentSignature, studyConsent);
        }
        // Update user
        caller.setConsent(true);
        caller.setHealthCode(healthId.getCode());
        return caller;
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        final String healthCode = caller.getHealthCode();
        List<StudyConsent> consents = studyConsentDao.getConsents(study.getIdentifier());
        for (StudyConsent consent : consents) {
            if (userConsentDao.hasConsented(healthCode, consent)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public User withdrawConsent(User caller, Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        boolean withdrawn = false;

        String healthCode = caller.getHealthCode();
        List<StudyConsent> consents = studyConsentDao.getConsents(study.getIdentifier());
        for (StudyConsent consent : consents) {
            if (userConsentDao.hasConsented(healthCode, consent)) {
                userConsentDao.withdrawConsent(healthCode, consent);
                withdrawn = true;
            }
        }
        if (withdrawn) {
            publisher.publishEvent(new UserUnenrolledEvent(caller, study));
            caller.setConsent(false);
        }
        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        final StudyConsent consent = studyConsentDao.getConsent(study.getIdentifier());
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthCode(), consent);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        sendMailService.sendConsentAgreement(caller, consentSignature, consent);
    }

}
