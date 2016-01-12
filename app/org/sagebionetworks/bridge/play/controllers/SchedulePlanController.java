package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class SchedulePlanController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    @Autowired
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    public Result getSchedulePlans() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        // We don't filter plans when we return a list of all of them for developers.
        List<SchedulePlan> plans =  schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyId);
        return okResult(plans);
    }

    public Result createSchedulePlan() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        SchedulePlan plan = schedulePlanService.createSchedulePlan(study, planForm);
        return createdResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result getSchedulePlan(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        SchedulePlan plan = schedulePlanService.getSchedulePlan(studyId, guid);
        return okResult(plan);
    }

    public Result updateSchedulePlan(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setGuid(guid);
        SchedulePlan plan = schedulePlanService.updateSchedulePlan(study, planForm);
        
        return okResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result deleteSchedulePlan(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        schedulePlanService.deleteSchedulePlan(studyId, guid);
        return okResult("Schedule plan deleted.");
    }

}
