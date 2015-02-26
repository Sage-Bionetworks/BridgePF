package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataUserConsent;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(TranscribeConsentHandler.class);

    private ConsentService consentService;
    private ParticipantOptionsService optionsService;

    @Autowired
    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Autowired
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        // study and user from context
        Study study = context.getStudy();
        User user = context.getUser();

        // Call services to get attributes. hasUserConsented is just the presence of the UserConsent object.
        UserConsent userConsent = consentService.getUserConsent(study, user);
        boolean hasUserConsented = (userConsent != null);
        boolean isUserSharingData = optionsService.getBooleanOption(user.getHealthCode(),
                ParticipantOptionsDao.Option.DATA_SHARING);
        boolean hasUserSignedMostRecentConsent = consentService.hasUserSignedMostRecentConsent(study, user);

        // If the user has consented, when did they consent?
        DateTime userConsentedOn = null;
        if (hasUserConsented) {
            userConsentedOn = new DateTime(userConsent.getSignedOn());
        }

        // add to the health data record builder
        HealthDataUserConsent userConsentMetadata = new HealthDataUserConsent.Builder()
                .withUserConsented(hasUserConsented).withUserConsentedOn(userConsentedOn)
                .withUserSharingData(isUserSharingData).withUserSignedMostRecentConsent(hasUserSignedMostRecentConsent)
                .build();
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        recordBuilder.withUserConsentMetadata(userConsentMetadata);

        // debug messages to help debug
        if (logger.isDebugEnabled()) {
            try {
                String recordJson = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(
                        context.getHealthDataRecordBuilder().build());
                logger.debug(String.format("Health Data Record: %s", recordJson));
                logger.debug(String.format("Attachments: %s", Joiner.on(", ").join(
                        context.getAttachmentsByFieldName().keySet())));
            } catch (JsonProcessingException ex) {
                logger.debug("Couldn't convert record builder into JSON", ex);
            }
        }
    }
}
