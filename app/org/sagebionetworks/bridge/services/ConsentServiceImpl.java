package org.sagebionetworks.bridge.services;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
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

    public UserSession give(String sessionToken, ResearchConsent consent, Study study, boolean sendEmail) throws BridgeServiceException {
        if (StringUtils.isBlank(sessionToken)) {
            throw new BridgeServiceException("Session token is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (study == null){
            throw new BridgeServiceException("Study is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (consent == null){
            throw new BridgeServiceException("ResearchConsent is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(consent.getName())){
            throw new BridgeServiceException("Consent signature is required.", HttpStatus.SC_BAD_REQUEST);
        } else if (consent.getBirthdate() == null){
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
            String key = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            // TODO: Save in ConsentDao and deprecate this in CustomData
            customData.put(key, "true");
            customData.save();
            
            // Email
            if (sendEmail) {
                sendMailService.sendConsentAgreement(session.getUser().getEmail(), consent, study);    
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

    private HealthId getHealthId(String healthIdKey, CustomData customData) {
        Object healthIdObj = customData.get(healthIdKey);
        if (healthIdObj != null) {
            final String healthId = healthCodeEncryptor.decrypt((String)healthIdObj);
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
