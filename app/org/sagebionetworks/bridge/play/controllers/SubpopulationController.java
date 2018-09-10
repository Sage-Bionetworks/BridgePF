package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.SubpopulationService;

import play.mvc.Result;

@Controller
public class SubpopulationController extends BaseController {
    
    private SubpopulationService subpopService;
    
    @Autowired
    public final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    public Result getAllSubpopulations(String includeDeleted) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<Subpopulation> subpopulations = subpopService.getSubpopulations(session.getStudyIdentifier(), Boolean.valueOf(includeDeleted));
        
        String ser = Subpopulation.SUBPOP_WRITER.writeValueAsString(new ResourceList<Subpopulation>(subpopulations));
        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    public Result createSubpopulation() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Subpopulation subpop = parseJson(request(), Subpopulation.class);
        subpop = subpopService.createSubpopulation(study, subpop);
        
        return createdResult(new GuidVersionHolder(subpop.getGuidString(), subpop.getVersion()));
    }
    public Result updateSubpopulation(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Subpopulation subpop = parseJson(request(), Subpopulation.class);
        subpop.setGuidString(guid);
        
        subpop = subpopService.updateSubpopulation(study, subpop);
        
        return okResult(new GuidVersionHolder(subpop.getGuidString(), subpop.getVersion()));
    }
    public Result getSubpopulation(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);

        Subpopulation subpop = subpopService.getSubpopulation(session.getStudyIdentifier(), subpopGuid);
        
        String ser = Subpopulation.SUBPOP_WRITER.writeValueAsString(subpop);
        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    public Result deleteSubpopulation(String guid, String physical) {
        UserSession session = getAuthenticatedSession(ADMIN, DEVELOPER);

        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            subpopService.deleteSubpopulationPermanently(session.getStudyIdentifier(), subpopGuid);
        } else {
            subpopService.deleteSubpopulation(session.getStudyIdentifier(), subpopGuid);
        }
        return okResult("Subpopulation has been deleted.");
    }

}
