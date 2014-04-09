package controllers;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.repo.model.auth.Session;

import play.mvc.*;

@org.springframework.stereotype.Controller
public class Authentication extends BaseController {

	public SynapseClient synapseClient;
	
	/**
	 * TODO: What happens if an exception is thrown
	 * TODO: How do we get the values
	 * TODO: We just set a cookie? That's it? I think we might
	 * want to return the profile to the user as well.
	 * @return
	 */
	public Result signIn() throws Exception {
		Session session = synapseClient.login("", "");
		String sessionToken = session.getSessionToken();
		// check terms of use.
		// set cookie with token.
		
		return jsonMessage(200, "Signed in.");
	}
	
	public Result signOut() {
		return jsonError(500, "Sign out not implemented.");
	}
	
	public Result forgotPassword() {
		return jsonError(500, "Forgot password not implemented.");
	}

	public Result resetPassword() {
		return jsonError(500, "Reset password not implemented.");
	}
}
