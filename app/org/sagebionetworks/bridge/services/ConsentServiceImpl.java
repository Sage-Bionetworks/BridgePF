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
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            final String healthCode = caller.getHealthDataCode();
            List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
            for (StudyConsent consent : consents) {
                if (userConsentDao.hasConsented(healthCode, consent)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public UserConsent getUserConsent(User caller, Study study) {
        checkNotNull(caller, "User is required");
        checkNotNull(study, "Study is required");
        try {
            final String healthCode = caller.getHealthDataCode();
            List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
            for (StudyConsent consent : consents) {
                if (userConsentDao.hasConsented(healthCode, consent)) {
                    return userConsentDao.getUserConsent(healthCode, consent);
                }
            }
            return null;
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public User withdrawConsent(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        String healthCode = caller.getHealthDataCode();
        List<StudyConsent> consents = studyConsentDao.getConsents(study.getKey());
        for (StudyConsent consent : consents) {
            if (userConsentDao.hasConsented(healthCode, consent)) {
                userConsentDao.withdrawConsent(healthCode, consent);
                publisher.publishEvent(new UserUnenrolledEvent(caller, study));
            }
        }
        caller.setConsent(false);
        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            final StudyConsent consent = studyConsentDao.getConsent(study.getKey());
            if (consent == null) {
                throw new BridgeServiceException("Consent not found.");
            }
            ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthDataCode(), consent);
            if (consentSignature == null) {
                throw new BridgeServiceException("Consent signature not found.");
            }
            sendMailService.sendConsentAgreement(caller, consentSignature, consent);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public User suspendDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.suspendSharing(caller.getHealthDataCode(), studyConsent);
            caller.setDataSharing(false);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
        return caller;
    }

    @Override
    public User resumeDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.resumeSharing(caller.getHealthDataCode(), studyConsent);
            caller.setDataSharing(true);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
        return caller;
    }

    @Override
    public boolean isSharingData(User caller, Study study) {
        if (caller == null) {
            throw new BadRequestException("User is required.");
        } else if (study == null) {
            throw new BadRequestException("Study is required.");
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            return userConsentDao.isSharingData(caller.getHealthDataCode(), studyConsent);
        } catch (Exception e) {
            throw new BridgeServiceException(e);
        }
    }
}
