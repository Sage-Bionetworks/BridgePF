package controllers;

import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.bridge.stubs.SynapseBootstrap;

import play.mvc.*;

@org.springframework.stereotype.Controller
public class Application extends BaseController {

    public Result redirectToApp() {
    	return redirect("/index.html"); 
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
