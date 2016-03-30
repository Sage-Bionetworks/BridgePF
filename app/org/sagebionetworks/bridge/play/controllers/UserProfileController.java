package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ExternalIdService;
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
    
    private ConsentService consentService;
    
    private ExternalIdService externalIdService;
    
    private ViewCache viewCache;

    @Autowired
    public final void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    @Autowired
    public final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    @Autowired
    public final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
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
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        ExternalIdentifier externalId = parseJson(request(), ExternalIdentifier.class);

        externalIdService.assignExternalId(study, externalId.getIdentifier(), session.getUser().getHealthCode());
        
        return okResult("External identifier added to user profile.");
    }
    
    public Result getDataGroups() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        Set<String> dataGroups = optionsService.getOptions(
                session.getUser().getHealthCode()).getStringSet(DATA_GROUPS);
        
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
        
        CriteriaContext context = getCriteriaContext(session);
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        user.setConsentStatuses(statuses);
                
        updateSessionUser(session, user);
        return okResult("Data groups updated.");
    }

}
