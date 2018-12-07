package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdServiceV4;

@Controller("externalIdController")
public class ExternalIdController extends BaseController {
    
    private static final TypeReference<List<String>> EXTERNAL_ID_TYPE_REF = new TypeReference<List<String>>() {};

    private ExternalIdServiceV4 externalIdService;
    
    @Autowired
    final void setExternalIdService(ExternalIdServiceV4 externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    public Result getExternalIds(String offsetKey, String pageSizeString, String idFilter, String assignmentFilterString) {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);

        // Play will not convert these to null if they are not included in the query string, so we must do the conversion.
        Integer pageSize = (pageSizeString != null) ? Integer.parseInt(pageSizeString,10) : null;
        Boolean assignmentFilter = (assignmentFilterString != null) ? Boolean.valueOf(assignmentFilterString) : null;

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = externalIdService.getExternalIds(
                offsetKey, pageSize, idFilter, assignmentFilter);
        return okResult(page);
    }
    
    public Result addExternalIds() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        List<String> identifiers = MAPPER.convertValue(requestToJSON(request()), EXTERNAL_ID_TYPE_REF);
        
        for (String externalIdentifier : identifiers) {
            ExternalIdentifier extId = ExternalIdentifier.create(study, externalIdentifier);
            externalIdService.createExternalIdentifier(extId);    
        }
        return createdResult("External identifiers added.");
    }
    
    public Result deleteExternalIds() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        String[] externalIds = request().queryString().get("externalId");
        if (externalIds == null || externalIds.length == 0) {
            throw new BadRequestException("No external IDs provided in query string.");
        }
        List<String> identifiers = Lists.newArrayList(externalIds);
        for (String externalIdentifier : identifiers) {
            ExternalIdentifier extId = ExternalIdentifier.create(study, externalIdentifier);
            externalIdService.deleteExternalIdentifier(extId);
        }
        
        
        return okResult("External identifiers deleted.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result generatePassword(String externalId, boolean createAccount) throws Exception {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        GeneratedPassword password = authenticationService.generatePassword(study, externalId, createAccount);
        
        return okResult(password);
    }

}
