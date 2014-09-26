package org.sagebionetworks.bridge.services;

import static org.apache.commons.httpclient.HttpStatus.SC_BAD_REQUEST;
import static org.apache.commons.httpclient.HttpStatus.SC_INTERNAL_SERVER_ERROR;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

public class ConsentServiceImpl implements ConsentService, ApplicationEventPublisherAware {

    private Client stormpathClient;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;
    private ApplicationEventPublisher publisher;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
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
    public User consentToResearch(User caller, ConsentSignature consentSignature, final Study study, boolean sendEmail)
            throws BridgeServiceException {

        if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        } else if (consentSignature == null) {
            throw new BridgeServiceException("Consent signature is required.", SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(consentSignature.getName())) {
            throw new BridgeServiceException("Consent full name is required.", SC_BAD_REQUEST);
        } else if (consentSignature.getBirthdate() == null) {
            throw new BridgeServiceException("Consent birth date  is required.", SC_BAD_REQUEST);
        }

        try {
            // Stormpath account
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();

            // HealthID
            final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            HealthId healthId = getHealthId(healthIdKey, customData); // This sets the ID, which we will need when fully

            {
                // TODO: Old. To be removed.
                customData.put(study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX, "true");
                customData.save();
                // TODO: New
                StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
                if (studyConsent == null) {
                    // TODO: To be removed once DynamoDB's study consent is ready.
                    //       This is kept here for testing purpose
                    studyConsent = new StudyConsent() {
                        @Override
                        public String getStudyKey() {
                            return study.getKey();
                        }

                        @Override
                        public long getCreatedOn() {
                            return 1406325157000L; // July 25, 2014
                        }

                        @Override
                        public boolean getActive() {
                            return true;
                        }

                        @Override
                        public String getPath() {
                            return "conf/email-templates/neurod-consent.html";
                        }

                        @Override
                        public int getMinAge() {
                            return 17;
                        }

                    };
                }
                userConsentDao.giveConsent(healthId.getCode(), studyConsent, consentSignature);
                publisher.publishEvent(new UserEnrolledEvent(caller, study));
            }

            if (sendEmail) {
                sendMailService.sendConsentAgreement(caller, consentSignature, study);
            }
            caller.setConsent(true);
            return caller;

        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            // TODO: Old. To be removed
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();
            boolean consented = ("true".equals(customData.get(study.getKey()
                    + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX)));
            return consented;
        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public User withdrawConsent(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            // TODO: Old
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();
            customData.remove(study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX);
            customData.save();
            // TODO: New
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

        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void emailConsentAgreement(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            StudyConsent consent = studyConsentDao.getConsent(study.getKey());
            if (consent == null) {
                throw new BridgeServiceException("Consent not found.", SC_INTERNAL_SERVER_ERROR);
            }
            ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthDataCode(), consent);
            if (consentSignature == null) {
                throw new BridgeServiceException("Consent signature not found.", SC_INTERNAL_SERVER_ERROR);
            }
            sendMailService.sendConsentAgreement(caller, consentSignature, study);
        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public User suspendDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.suspendSharing(caller.getHealthDataCode(), studyConsent);

            caller.setDataSharing(false);
        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
        return caller;
    }

    @Override
    public User resumeDataSharing(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.resumeSharing(caller.getHealthDataCode(), studyConsent);
            caller.setDataSharing(true);
        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
        return caller;
    }

    @Override
    public boolean isSharingData(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", SC_BAD_REQUEST);
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            return userConsentDao.isSharingData(caller.getHealthDataCode(), studyConsent);
        } catch (Exception e) {
            throw new BridgeServiceException(e, SC_INTERNAL_SERVER_ERROR);
        }
    }

    private HealthId getHealthId(String healthIdKey, CustomData customData) {
        Object healthIdObj = customData.get(healthIdKey);
        if (healthIdObj != null) {
            final String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);
            final String healthCode = healthCodeService.getHealthCode(healthId);
            return new HealthId() {
                @Override
                public String getId() {
                    return healthId;
                }

                @Override
                public String getCode() {
                    return healthCode;
                }
            };
        }
        HealthId healthId = healthCodeService.create();
        customData.put(healthIdKey, healthCodeEncryptor.encrypt(healthId.getId()));
        customData.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
        customData.save();
        return healthId;
    }
}
