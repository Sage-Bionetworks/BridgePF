package org.sagebionetworks.bridge.services;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

public class ConsentServiceImpl implements ConsentService {

    private Client stormpathClient;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

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
    public User consentToResearch(User caller, ResearchConsent researchConsent, Study study,
            boolean sendEmail) throws BridgeServiceException {

        if (caller == null) {
            throw new BridgeServiceException("User is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (researchConsent == null) {
            throw new BridgeServiceException("ResearchConsent is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(researchConsent.getName())) {
            throw new BridgeServiceException("Consent signature is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (researchConsent.getBirthdate() == null) {
            throw new BridgeServiceException("Consent birth date  is required.", HttpStatus.SC_BAD_REQUEST);
        }

        try {
            // Stormpath account
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();

            // HealthID
            final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            getHealthId(healthIdKey, customData); // This sets the ID, which we will need when fully implemented

            customData.put(study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX, "true");
            customData.save();
            caller.setConsent(true);
            
            if (sendEmail) {
                sendMailService.sendConsentAgreement(caller, researchConsent, study);
            }
            return caller;
            
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();

            return ("true".equals(customData.get(study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX)));
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
     }

    @Override
    public void withdrawConsent(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            final Account account = stormpathClient.getResource(caller.getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();
            customData.remove(study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX);
            customData.save();
            caller.setConsent(false);
            /*
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            userConsentDao.withdrawConsent(caller.getHealthDataCode(), studyConsent);
            caller.setConsent(false);
            */
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void emailConsentAgreement(User caller, Study study) {
        if (caller == null) {
            throw new BridgeServiceException("User is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(study.getKey());
            ResearchConsent consent = userConsentDao.getConsentSignature(caller.getHealthDataCode(), studyConsent);
            if (studyConsent == null || consent == null) {
                throw new BridgeServiceException("Study Consent or Consent Signature not found.",
                        HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            sendMailService.sendConsentAgreement(caller, consent, study);
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public User suspendDataSharing(User caller, Study study) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public User resumeDataSharing(User caller, Study study) {
        // TODO Auto-generated method stub
        return null;
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
