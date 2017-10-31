package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.services.IntentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("intentController")
public class IntentController extends BaseController {

    private IntentService intentService;
    
    @Autowired
    final void setIntentService(IntentService intentService) {
        this.intentService = intentService;
    }
    
    public Result submitIntentToParticipate() throws Exception {
        IntentToParticipate intent = parseJson(request(), IntentToParticipate.class);
        
        intentService.submitIntentToParticipate(intent);
        
        return acceptedResult("Intent to participate accepted.");
    }
    
}
