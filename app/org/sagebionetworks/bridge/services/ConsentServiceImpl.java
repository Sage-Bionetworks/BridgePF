package org.sagebionetworks.bridge.services;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.UserSession;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

public class ConsentServiceImpl implements ConsentService {

    private Client stormpathClient;
    private CacheProvider cache;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cache = cache;
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
    public UserSession consentToResearch(String sessionToken, ResearchConsent researchConsent, Study study,
            boolean sendEmail) throws BridgeServiceException {
        if (StringUtils.isBlank(sessionToken)) {
            throw new BridgeServiceException("Session token is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (researchConsent == null) {
            throw new BridgeServiceException("ResearchConsent is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(researchConsent.getName())) {
            throw new BridgeServiceException("Consent signature is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (researchConsent.getBirthdate() == null) {
            throw new BridgeServiceException("Consent birth date  is required.", HttpStatus.SC_BAD_REQUEST);
        }
        final UserSession session = cache.getUserSession(sessionToken);
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", HttpStatus.SC_FORBIDDEN);
        }

        try {
            // Stormpath account
            final Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);
            final CustomData customData = account.getCustomData();

            // HealthID
            final String healthIdKey = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            final HealthId healthId = getHealthId(healthIdKey, customData);

            // Write consent
            StudyConsent studyConsent = studyConsentDao.getConsent(session.getStudyKey());

            if (studyConsent == null) {
                String path = study.getConsentAgreement().getURL().toString();
                studyConsent = studyConsentDao.addConsent(session.getStudyKey(), path, study.getMinAge());
                studyConsentDao.setActive(studyConsent);
            }
            if (!userConsentDao.hasConsented(healthId.getCode(), studyConsent)) {
                userConsentDao.giveConsent(healthId.getCode(), studyConsent, researchConsent);
            }

            // Email
            if (sendEmail) {
                sendMailService.sendConsentAgreement(session.getUser().getEmail(), researchConsent, study);
            }

            // Update session
            session.setHealthDataCode(healthId.getCode());
            session.setConsent(true);
            cache.setUserSession(sessionToken, session);
            return session;

        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public UserSession withdrawConsent(String sessionToken, Study study) {
        if (StringUtils.isBlank(sessionToken)) {
            throw new BridgeServiceException("Session token required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        }
        UserSession session = cache.getUserSession(sessionToken);
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", HttpStatus.SC_UNAUTHORIZED);
        } else if (!session.doesConsent()) {
            throw new BridgeServiceException("Consent is required.", HttpStatus.SC_BAD_REQUEST);
        }

        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(session.getStudyKey());
            userConsentDao.withdrawConsent(session.getHealthDataCode(), studyConsent);
            session.setConsent(false);
            cache.setUserSession(session.getSessionToken(), session);
            return session;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void emailConsentAgreement(String sessionToken, Study study) {
        if (StringUtils.isBlank(sessionToken)) {
            throw new BridgeServiceException("Session token is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        }

        UserSession session = cache.getUserSession(sessionToken);
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", HttpStatus.SC_UNAUTHORIZED);
        } else if (!session.doesConsent()) {
            throw new BridgeServiceException("Consent is required.", HttpStatus.SC_BAD_REQUEST);
        }

        try {
            StudyConsent studyConsent = studyConsentDao.getConsent(session.getStudyKey());
            ResearchConsent consent = userConsentDao.getConsentSignature(session.getHealthDataCode(), studyConsent);
            if (studyConsent == null || consent == null) {
                throw new BridgeServiceException("Study Consent or Consent Signature not found.",
                        HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            sendMailService.sendConsentAgreement(session.getUser().getEmail(), consent, study);
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public UserSession suspendDataSharing(String sessionToken, Study study) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserSession resumeDataSharing(String sessionToken, Study study) {
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
