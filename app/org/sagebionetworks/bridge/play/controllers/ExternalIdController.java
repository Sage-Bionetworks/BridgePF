package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

import com.fasterxml.jackson.core.type.TypeReference;

import play.mvc.Result;

@Controller("externalIdController")
public class ExternalIdController extends BaseController {
    
    private static final TypeReference<List<String>> EXTERNAL_ID_TYPE_REF = new TypeReference<List<String>>() {};

    private ExternalIdService externalIdService;
    
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    public Result getExternalIds(String offsetKey, Integer pageSize, String idFilter, Boolean assignmentFilter) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        PagedResourceList<String> page = externalIdService.getExternalIds(study, offsetKey, pageSize, idFilter, assignmentFilter);
        return okResult(page);
    }
    
    public Result addExternalIds() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        List<String> externalIdentifiers = MAPPER.convertValue(requestToJSON(request()), EXTERNAL_ID_TYPE_REF);
        externalIdService.addExternalIds(study, externalIdentifiers);
        
        return createdResult("External identifiers added.");
    }

}
