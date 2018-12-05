package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdServiceV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

@Controller("externalIdControllerV4")
public class ExternalIdControllerV4 extends BaseController {

    private ExternalIdServiceV4 externalIdService;
    
    @Autowired
    final void setExternalIdService(ExternalIdServiceV4 externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    public Result getExternalIdentifiers(String offsetKey, String pageSizeString, String idFilter,
            String assignmentFilterString) {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);

        Integer pageSize = BridgeUtils.getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        Boolean assignmentFilter = "true".equals(assignmentFilterString);
        
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = externalIdService.getExternalIds(
                offsetKey, pageSize, idFilter, assignmentFilter);
        return okResult(page);
    }
    
    public Result createExternalIdentifier() {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);
        
        ExternalIdentifier externalIdentifier = parseJson(request(), ExternalIdentifier.class);
        externalIdService.createExternalIdentifier(externalIdentifier);
        
        return createdResult("External identifier created.");
    }
    
    public Result deleteExternalIdentifier(String externalId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        externalIdService.deleteExternalId(study, externalId);
        
        return okResult("External identifier deleted.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result generatePassword(String externalId, boolean createAccount) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        GeneratedPassword password = authenticationService.generatePassword(study, externalId, createAccount);
        
        return okResult(password);
    }
}
