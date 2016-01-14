package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.UserProfileService;
import org.sagebionetworks.bridge.validators.DataGroupsValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.common.base.Supplier;

import play.mvc.Result;

@Controller
public class UserProfileController extends BaseController {
    
    private UserProfileService userProfileService;
    
    private ParticipantOptionsService optionsService;
    
    private ConsentService consentService;
    
    private ViewCache viewCache;

    @Autowired
    public final void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    @Autowired
    public final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    public final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public Result getUserProfile() throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        
        ViewCacheKey<UserProfile> cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getId(), study.getIdentifier());
        String json = viewCache.getView(cacheKey, new Supplier<UserProfile>() {
            @Override public UserProfile get() {
                return userProfileService.getProfile(study, session.getUser().getEmail());
            }
        });
        return ok(json).as(BridgeConstants.JSON_MIME_TYPE);
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        User user = session.getUser();
        UserProfile profile = UserProfile.fromJson(study.getUserProfileAttributes(), requestToJSON(request()));
        user = userProfileService.updateProfile(study, user, profile);
        updateSessionUser(session, user);
        
        ViewCacheKey<UserProfile> cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getId(), study.getIdentifier());
        viewCache.removeView(cacheKey);
        
        return okResult("Profile updated.");
    }
    
    public Result createExternalIdentifier() throws Exception {
        UserSession session = getAuthenticatedSession();

        ExternalIdentifier externalId = parseJson(request(), ExternalIdentifier.class);

        optionsService.setString(session.getStudyIdentifier(), session.getUser().getHealthCode(), EXTERNAL_IDENTIFIER,
                externalId.getIdentifier());
        
        return okResult("External identifier added to user profile.");
    }
    
    public Result getDataGroups() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        Set<String> dataGroups = optionsService.getStringSet(session.getUser().getHealthCode(), DATA_GROUPS);
        
        return okResult(new DataGroups(dataGroups));
    }
    
    public Result updateDataGroups() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DataGroups dataGroups = parseJson(request(), DataGroups.class);
        Validate.entityThrowingException(new DataGroupsValidator(study.getDataGroups()), dataGroups);
        
        optionsService.setStringSet(session.getStudyIdentifier(), 
                session.getUser().getHealthCode(), DATA_GROUPS, dataGroups.getDataGroups());
        
        User user = session.getUser();
        user.setDataGroups(dataGroups.getDataGroups());

        ScheduleContext context = new ScheduleContext.Builder()
                .withUser(user)
                .withClientInfo(getClientInfoFromUserAgentHeader())
                .withUserDataGroups(dataGroups.getDataGroups())
                .build();
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        user.setConsentStatuses(statuses);
                
        updateSessionUser(session, user);
        return okResult("Data groups updated.");
    }

}
