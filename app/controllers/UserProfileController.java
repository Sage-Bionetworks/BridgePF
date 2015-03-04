package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.common.base.Supplier;

import play.mvc.Result;

@Controller("userProfileController")
public class UserProfileController extends BaseController {
    
    private UserProfileService userProfileService;
    
    private ViewCache viewCache;

    @Autowired
    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    @Autowired
    public void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    public Result getUserProfile() throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        ViewCacheKey<UserProfile> cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getEmail(), study.getIdentifier());
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
        UserProfile profile = UserProfile.fromJson(requestToJSON(request()));
        user = userProfileService.updateProfile(study, user, profile);
        updateSessionUser(session, user);
        
        ViewCacheKey<UserProfile> cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getEmail(), study.getIdentifier());
        viewCache.removeView(cacheKey);
        
        return okResult("Profile updated.");
    }
}
