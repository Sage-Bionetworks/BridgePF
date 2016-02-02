package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Sets;

import play.mvc.Result;

@Controller
public class SubpopulationController extends BaseController {

    private static final Set<Roles> DELETE_ROLES = Sets.newHashSet(ADMIN, DEVELOPER);
    
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
    public Result getSubpopulation(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);

        Subpopulation subpop = subpopService.getSubpopulation(session.getStudyIdentifier(), subpopGuid);
        return okResult(subpop);
    }
    public Result deleteSubpopulation(String guid, String physicalDeleteString) {
        UserSession session = getAuthenticatedSession();
        if (!session.getUser().isInRole(DELETE_ROLES)) {
            throw new UnauthorizedException();
        }
        // Only admins can request a physical delete.
        boolean physicalDelete = ("true".equals(physicalDeleteString));
        if (physicalDelete && !session.getUser().isInRole(ADMIN)) {
            throw new UnauthorizedException();
        }
        
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        subpopService.deleteSubpopulation(session.getStudyIdentifier(), subpopGuid, physicalDelete);

        String message = (physicalDelete) ? "Subpopulation has been permanently deleted." : "Subpopulation has been deleted.";
        return okResult(message);
    }

}
