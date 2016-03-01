package org.sagebionetworks.bridge.upload;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Set;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.UserOptionsLookup;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private ParticipantOptionsService optionsService;

    @Autowired
    public final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        // read sharing scope from options service
        UserOptionsLookup lookup = optionsService.getOptions(
                context.getUpload().getHealthCode());

        // Options service lookup would return null if no value has been set for this user. 
        // If it does, default to no_sharing
        SharingScope userSharingScope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);
        if (userSharingScope == null) {
            userSharingScope = SharingScope.NO_SHARING;
        }

        // Also get external ID
        String userExternalId = lookup.getString(EXTERNAL_IDENTIFIER);
        
        // And get user data groups
        Set<String> userDataGroups = lookup.getStringSet(DATA_GROUPS);

        // write sharing scope to health data record
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        recordBuilder.withUserSharingScope(userSharingScope).withUserExternalId(userExternalId)
                .withUserDataGroups(userDataGroups);
    }
}
