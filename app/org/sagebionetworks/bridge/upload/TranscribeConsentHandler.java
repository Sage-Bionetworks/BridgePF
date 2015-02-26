package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.healthdata.HealthDataUserConsent;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
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
    }
}
