package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserProfileService;

import com.google.common.base.Supplier;

import play.mvc.Result;

public class UserProfileController extends BaseController {
    
    private UserProfileService userProfileService;
    
    private ViewCache viewCache;
    
    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    
    public void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    public Result getUserProfile() throws Exception {
        final UserSession session = getAuthenticatedSession();
        final String studyIdentifier = getStudyIdentifier();

        ViewCacheKey cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getEmail(), studyIdentifier);
        String json = viewCache.getView(cacheKey, new Supplier<UserProfile>() {
            @Override public UserProfile get() {
                return userProfileService.getProfile(session.getUser().getEmail());
            }
        });
        return ok(json).as(BridgeConstants.JSON_MIME_TYPE);
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = getAuthenticatedSession();
        final String studyIdentifier = getStudyIdentifier();
        
        User user = session.getUser();
        UserProfile profile = UserProfile.fromJson(requestToJSON(request()));
        user = userProfileService.updateProfile(user, profile);
        updateSessionUser(session, user);
        
        ViewCacheKey cacheKey = viewCache.getCacheKey(UserProfile.class, session.getUser().getEmail(), studyIdentifier);
        viewCache.removeView(cacheKey);
        
        return okResult("Profile updated.");
    }
}
