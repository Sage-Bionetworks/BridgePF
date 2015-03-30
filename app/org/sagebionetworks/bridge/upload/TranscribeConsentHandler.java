package org.sagebionetworks.bridge.upload;

import java.util.Map;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private ParticipantOptionsService optionsService;

    @Autowired
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        // read sharing scope from options service
        User user = context.getUser();
        
        Map<ParticipantOption,String> options = optionsService.getAllParticipantOptions(user.getHealthCode());
        SharingScope userSharingScope = SharingScope.valueOf(options.get(ParticipantOption.SHARING_SCOPE));
        String userExternalId = options.get(ParticipantOption.EXTERNAL_IDENTIFIER);

        // write sharing scope to health data record
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        recordBuilder.withUserSharingScope(userSharingScope).withUserExternalId(userExternalId);
        
    }
}
