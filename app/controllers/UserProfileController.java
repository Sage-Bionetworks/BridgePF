package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserProfileService;

import play.mvc.Result;

public class UserProfileController extends BaseController {
	private UserProfileService userProfileService;
	
	public void setUserProfileService(UserProfileService userProfileService) {
	    this.userProfileService = userProfileService;
	}
	
	public Result getUserProfile() throws Exception {
		UserSession session = getSession();
		User user = userProfileService.getUser(session);
		return jsonResult(new JsonPayload<User>(user));
	}
	
	public Result updateUserProfile() throws Exception {
		UserSession session = getSession();
		User updatedUser = User.fromJson(requestToJSON(request()), session);
		userProfileService.updateUser(updatedUser, session);
		
	    return jsonResult("Profile updated.");
	}
}
