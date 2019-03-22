package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class SubstudyController extends BaseController {

    private static final String INCLUDE_DELETED = "includeDeleted";
    private SubstudyService service;
    
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.service = substudyService;
    }
    
    public Result getSubstudies(String includeDeletedStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        boolean includeDeleted = Boolean.valueOf(includeDeletedStr);
        
        List<Substudy> substudies = service.getSubstudies(session.getStudyIdentifier(), includeDeleted);
        
        return okResult(new ResourceList<>(substudies).withRequestParam(INCLUDE_DELETED, includeDeleted));
    }
    
    public Result createSubstudy() {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        Substudy substudy = parseJson(request(), Substudy.class);
        VersionHolder holder = service.createSubstudy(session.getStudyIdentifier(), substudy);
        
        return createdResult(holder);
    }
    
    public Result getSubstudy(String id) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        Substudy substudy = service.getSubstudy(session.getStudyIdentifier(), id, true);
        
        return okResult(substudy);
    }
    
    public Result updateSubstudy(String id) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        Substudy substudy = parseJson(request(), Substudy.class);
        VersionHolder holder = service.updateSubstudy(session.getStudyIdentifier(), substudy);
        
        return okResult(holder);
    }
    
    public Result deleteSubstudy(String id, String physical) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        if ("true".equals(physical) && session.isInRole(Roles.ADMIN)) {
            service.deleteSubstudyPermanently(session.getStudyIdentifier(), id);
        } else {
            service.deleteSubstudy(session.getStudyIdentifier(), id);    
        }
        return okResult("Substudy deleted.");
    }

}
