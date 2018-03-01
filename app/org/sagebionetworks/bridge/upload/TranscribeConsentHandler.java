package org.sagebionetworks.bridge.upload;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Set;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
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
        ParticipantOptionsLookup lookup = optionsService.getOptions(context.getStudy(), context.getHealthCode());

        // Get sharing scope (defaults to NO_SHARING)
        SharingScope userSharingScope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);

        // Also get external ID
        String userExternalId = lookup.getString(EXTERNAL_IDENTIFIER);
        
        // And get user data groups
        Set<String> userDataGroups = lookup.getStringSet(DATA_GROUPS);

        // write sharing scope to health data record
        HealthDataRecord record = context.getHealthDataRecord();
        record.setUserSharingScope(userSharingScope);
        record.setUserExternalId(userExternalId);
        record.setUserDataGroups(userDataGroups);
    }
}
