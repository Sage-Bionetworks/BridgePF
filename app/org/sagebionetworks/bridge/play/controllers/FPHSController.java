package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.FPHSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;

import play.mvc.Result;

@Controller("fphsController")
public class FPHSController extends BaseController {
    
    private static final StudyIdentifier FPHS_ID = new StudyIdentifierImpl("fphs");
    
    private static final TypeReference<List<FPHSExternalIdentifier>> EXTERNAL_ID_TYPE_REF = 
            new TypeReference<List<FPHSExternalIdentifier>>() {};

    private FPHSService fphsService;
    
    private ConsentService consentService;
    
    @Autowired
    public final void setFPHSService(FPHSService service) {
        this.fphsService = service; 
    }
    
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService; 
    }
    
    public Result verifyExternalIdentifier(String identifier) throws Exception {
        // public API, no restrictions. externalId can be null so we can create a 400 error in the service.
        ExternalIdentifier externalId = ExternalIdentifier.create(FPHS_ID, identifier);
        fphsService.verifyExternalIdentifier(externalId);
        return okResult(FPHSExternalIdentifier.create(externalId.getIdentifier()));
    }
    public Result registerExternalIdentifier() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        ExternalIdentifier externalId = parseJson(request(), ExternalIdentifier.class);
        fphsService.registerExternalIdentifier(session.getStudyIdentifier(), session.getUser().getHealthCode(), externalId);

        User user = session.getUser();
        
        // The service saves the external identifier and saves this as an option. We also need 
        // to update the user's session, something that should be generalized for other methods
        // that change data groups.
        Set<String> dataGroups = Sets.newHashSet(user.getDataGroups());
        dataGroups.add("football_player");
        user.setDataGroups(dataGroups);
        
        CriteriaContext context = getCriteriaContext(session);
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        user.setConsentStatuses(statuses);
        updateSessionUser(session, user);
        
        return okResult("External identifier added to user profile.");
    }
    public Result getExternalIdentifiers() throws Exception {
        getAuthenticatedSession(ADMIN);
        
        List<FPHSExternalIdentifier> identifiers = fphsService.getExternalIdentifiers();
        
        return okResult(identifiers);
    }
    public Result addExternalIdentifiers() throws Exception {
        getAuthenticatedSession(ADMIN);
        
        List<FPHSExternalIdentifier> externalIds = MAPPER.convertValue(requestToJSON(request()), EXTERNAL_ID_TYPE_REF);
        fphsService.addExternalIdentifiers(externalIds);
        
        return createdResult("External identifiers added.");
    }
}
