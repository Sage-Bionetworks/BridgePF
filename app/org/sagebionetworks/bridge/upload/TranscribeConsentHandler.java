package org.sagebionetworks.bridge.upload;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

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
        Map<ParticipantOption,String> options = optionsService.getAllParticipantOptions(
                context.getUpload().getHealthCode());

        // Options service never returns null, but it may return an empty map. Check that it has a value for
        // sharing_scope, and if it doesn't, default to no_sharing
        String sharingScopeString = options.get(ParticipantOption.SHARING_SCOPE);
        SharingScope userSharingScope;
        if (isBlank(sharingScopeString)) {
            userSharingScope = SharingScope.NO_SHARING;
        } else {
            userSharingScope = SharingScope.valueOf(sharingScopeString);
        }

        // Also get external ID
        String userExternalId = options.get(ParticipantOption.EXTERNAL_IDENTIFIER);
        
        // And get user data groups
        String dataGroupsSer = options.get(ParticipantOption.DATA_GROUPS);
        Set<String> userDataGroups = (isNotBlank(dataGroupsSer))
                ? Sets.newHashSet(Splitter.on(",").split(dataGroupsSer)) : null;

        // write sharing scope to health data record
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        recordBuilder.withUserSharingScope(userSharingScope).withUserExternalId(userExternalId)
                .withUserDataGroups(userDataGroups);
    }
}
