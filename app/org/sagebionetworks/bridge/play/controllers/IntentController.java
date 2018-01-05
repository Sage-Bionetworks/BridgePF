package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.services.IntentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.JsonNode;

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
        
        // An early hack in the system was that sharing scope was added to the consent signature 
        // JSON even though it is not part of the signature. We need to move that value because 
        // the client API continues to treat sharing as part of the consent signature.
        JsonNode requestNode = requestToJSON(request());
        if (requestNode != null && requestNode.has("consentSignature")) {
            SharingOption sharing = SharingOption.fromJson(requestNode.get("consentSignature"), 2);
            intent = new IntentToParticipate.Builder().copyOf(intent)
                    .withScope(sharing.getSharingScope()).build();
        }
        intentService.submitIntentToParticipate(intent);
        
        return acceptedResult("Intent to participate accepted.");
    }
    
}
