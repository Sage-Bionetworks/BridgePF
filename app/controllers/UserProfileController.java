package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
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
        // TODO Need to remove this, and still use getSession(). getSession()
        // shouldn't return a 412 when called without consent, it should simply return the
        // session.
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        UserProfileInfo user = new UserProfileInfo(session.getUser());

        return jsonResult(new JsonPayload<UserProfileInfo>(user));
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        UserProfile currentUser = session.getUser();
        UserProfile updatedUser = UserProfile.fromJson(requestToJSON(request()), currentUser);
        userProfileService.updateUser(updatedUser, currentUser);

        session.setUser(updatedUser);
        cache.setUserSession(session.getSessionToken(), session);

        return jsonResult("Profile updated.");
    }
}
