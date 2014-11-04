package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
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
    public User consentToResearch(final User caller, final ConsentSignature consentSignature, final Study study,
            final boolean sendEmail) throws BridgeServiceException {
        if (caller.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        } else if (consentSignature == null) {
            throw new BadRequestException("Consent signature is required.");
        } else if (StringUtils.isBlank(consentSignature.getName())) {
            throw new BadRequestException("Consent full name is required.");
        } else if (consentSignature.getBirthdate() == null) {
            throw new BadRequestException("Consent birth date  is required.");
        }
        try {
            // Stormpath account
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            HealthId hid = accountEncryptionService.getHealthCode(study, account);
            if (hid == null) {
                accountEncryptionService.createAndSaveHealthCode(study, account);
            }
            final HealthId healthId = hid;
            // Give consent
            final StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.giveConsent(healthId.getCode(), studyConsent, consentSignature);
            // Publish event
            publisher.publishEvent(new UserEnrolledEvent(caller, study));
            // Sent email
            if (sendEmail) {
                sendMailService.sendConsentAgreement(caller, consentSignature, studyConsent);
            }
            // Update user
            caller.setConsent(true);
            caller.setHealthDataCode(healthId.getCode());
            return caller;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public UserConsent getUserConsent(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        try {
            StudyConsent consent = getStudyConsentUserConsentedTo(caller, study);
            if (consent != null) {
                return userConsentDao.getUserConsent(caller.getHealthDataCode(), consent);
            }
            return null;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public StudyConsent getStudyConsentUserConsentedTo(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        try {
            final String healthCode = caller.getHealthDataCode();
            List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
            for (StudyConsent consent : consents) {
                if (userConsentDao.hasConsented(healthCode, consent)) {
                    return consent;
                }
            }
            return null;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    

    @Override
    public User withdrawConsent(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        
        StudyConsent consent = getStudyConsentUserConsentedTo(caller, study);
        if (consent != null) {
            userConsentDao.withdrawConsent(caller.getHealthDataCode(), consent);
            publisher.publishEvent(new UserUnenrolledEvent(caller, study));
            caller.setConsent(false);
        }
        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        
        StudyConsent consent = getStudyConsentUserConsentedTo(caller, study);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class, "Consent not found.");
        }
        ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthDataCode(), consent);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class, "Consent signature not found.");
        }
        sendMailService.sendConsentAgreement(caller, consentSignature, consent);
    }

    @Override
    public User suspendDataSharing(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        
        StudyConsent consent = getStudyConsentUserConsentedTo(caller, study);
        if (consent != null) {
            userConsentDao.suspendSharing(caller.getHealthDataCode(), consent);
            caller.setDataSharing(false);
        }
        return caller;
    }

    @Override
    public User resumeDataSharing(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        
        StudyConsent consent = getStudyConsentUserConsentedTo(caller, study);
        if (consent != null) {
            userConsentDao.resumeSharing(caller.getHealthDataCode(), consent);
            caller.setDataSharing(true);
        }
        return caller;
    }
}
