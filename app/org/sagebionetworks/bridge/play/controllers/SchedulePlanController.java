package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
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
    
    public Result getSchedulePlansV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<SchedulePlan> plans =  schedulePlanService.getSchedulePlans(studyId);
        return okResult(plans);
    }

    public Result createSchedulePlanV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(studyId.getIdentifier());
        SchedulePlan plan = schedulePlanService.createSchedulePlan(planForm);
        return createdResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result getSchedulePlanV3(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        SchedulePlan plan = schedulePlanService.getSchedulePlan(studyId, guid);
        return okResult(plan);
    }

    public Result updateSchedulePlanV3(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(studyId.getIdentifier());
        SchedulePlan plan = schedulePlanService.updateSchedulePlan(planForm);
        
        return okResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result deleteSchedulePlanV3(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        schedulePlanService.deleteSchedulePlan(studyId, guid);
        return okResult("Schedule plan deleted.");
    }
    
    @Deprecated
    public Result getSchedulePlansV1() throws Exception {
        return getSchedulePlansV3();
    }
    @Deprecated
    public Result createSchedulePlanV1() throws Exception {
        return createSchedulePlanV3();
    }
    @Deprecated
    public Result getSchedulePlanV1(String guid) throws Exception {
        return getSchedulePlanV3(guid);
    }
    @Deprecated
    public Result updateSchedulePlanV1(String guid) throws Exception {
        return updateSchedulePlanV3(guid);
    }
    @Deprecated
    public Result deleteSchedulePlanV1(String guid) {
        return deleteSchedulePlanV3(guid);
    }
}
