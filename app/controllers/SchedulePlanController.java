package controllers;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("schedulePlanController")
public class SchedulePlanController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    @Autowired
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    public Result getSchedulePlans() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<SchedulePlan> plans =  schedulePlanService.getSchedulePlans(studyId);
        return okResult(plans);
    }

    public Result createSchedulePlan() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(studyId.getIdentifier());
        SchedulePlan plan = schedulePlanService.createSchedulePlan(planForm);
        return createdResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result getSchedulePlan(String guid) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        SchedulePlan plan = schedulePlanService.getSchedulePlan(studyId, guid);
        return okResult(plan);
    }

    public Result updateSchedulePlan(String guid) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(studyId.getIdentifier());
        SchedulePlan plan = schedulePlanService.updateSchedulePlan(planForm);
        
        return okResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result deleteSchedulePlan(String guid) {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        schedulePlanService.deleteSchedulePlan(studyId, guid);
        return okResult("Schedule plan deleted.");
    }

}
