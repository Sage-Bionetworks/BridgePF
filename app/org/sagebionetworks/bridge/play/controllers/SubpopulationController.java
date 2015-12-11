package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.SubpopulationService;

import play.mvc.Result;

@Controller
public class SubpopulationController extends BaseController {

    private SubpopulationService subpopService;
    
    @Autowired
    public final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    public Result getAllSubpopulations() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<Subpopulation> subpopulations = subpopService.getSubpopulations(session.getStudyIdentifier());
        return okResult(subpopulations);
    }
    public Result createSubpopulation() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Subpopulation subpop = parseJson(request(), Subpopulation.class);
        subpop = subpopService.createSubpopulation(study, subpop);
        
        return createdResult(new GuidVersionHolder(subpop.getGuid(), subpop.getVersion()));
    }
    public Result updateSubpopulation(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Subpopulation subpop = parseJson(request(), Subpopulation.class);
        subpop.setGuid(guid);
        subpop = subpopService.updateSubpopulation(study, subpop);
        
        return okResult(new GuidVersionHolder(subpop.getGuid(), subpop.getVersion()));
    }
    public Result getSubpopulation(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        Subpopulation subpop = subpopService.getSubpopulation(session.getStudyIdentifier(), guid);
        return okResult(subpop);
    }
    public Result deleteSubpopulation(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        subpopService.deleteSubpopulation(session.getStudyIdentifier(), guid);
        
        return okResult("Subpopulation has been deleted.");
    }

}
