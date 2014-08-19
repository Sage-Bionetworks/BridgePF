package controllers;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserProfileService;

import play.libs.Json;
import play.mvc.Result;

public class UserProfileController extends BaseController {
    
    private UserProfileService userProfileService;

    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public Result getUserProfile() throws Exception {
        UserSession session = getSession();
        UserProfile profile = new UserProfile(session.getUser());

        return ok(Json.toJson(profile));
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = getSession();
        User user = session.getUser();
        UserProfile profile = UserProfile.fromJson(requestToJSON(request()));
        user = userProfileService.updateProfile(user, profile);
        updateSessionUser(session, user);
        
        return okResult("Profile updated.");
    }
}
