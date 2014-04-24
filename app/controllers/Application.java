package controllers;

import models.UserSession;

import org.aopalliance.intercept.Interceptor;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.stubs.SynapseBootstrap;

import play.libs.Json;
import play.mvc.*;

@org.springframework.stereotype.Controller
public class Application extends BaseController {

	private AuthenticationService authenticationService;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public Result redirectToApp() {
		return redirect("/");
	}
	
    public Result loadApp() throws Exception {
		String sessionToken = getSessionToken(false);
		UserSession session = authenticationService.getSession(sessionToken);
    	return ok(views.html.index.render(Json.toJson(session).toString()));
    }
    
    // REMOVEME (when we figure out how to expose this as part of the build)
    public Result bootstrap() throws Exception {
    	if (StackConfiguration.isDevelopStack()) {
    		SynapseBootstrap bootstrapper = new SynapseBootstrap(new SynapseBootstrap.IntegrationClientProvider());
    		bootstrapper.create();
    		return jsonResult("Added some bootstrap users to you Synapse system.");
    	} else {
    		return notFound(); 
    	}
    }
    
}
