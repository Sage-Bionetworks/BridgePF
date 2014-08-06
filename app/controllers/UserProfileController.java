package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserProfileInfo;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserProfileService;

import play.mvc.Result;

public class UserProfileController extends BaseController {
    private UserProfileService userProfileService;
    private CacheProvider cache;

    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cache = cache;
    }

    public Result getUserProfile() throws Exception {
        UserSession session = getSession();
        UserProfileInfo user = new UserProfileInfo(session.getUser());

        return jsonResult(new JsonPayload<UserProfileInfo>(user));
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = getSession();
        UserProfile currentUser = session.getUser();
        UserProfile updatedUser = UserProfile.fromJson(requestToJSON(request()), currentUser);
        userProfileService.updateUser(updatedUser, currentUser);

        session.setUser(updatedUser);
        cache.setUserSession(session.getSessionToken(), session);

        return jsonResult("Profile updated.");
    }
}
