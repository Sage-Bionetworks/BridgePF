package controllers;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import play.mvc.Result;

public class SchedulePlanController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    public Result getSchedulePlans() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        List<SchedulePlan> plans =  schedulePlanService.getSchedulePlans(study);
        return okResult(plans);
    }

    public Result createSchedulePlan() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(study.getIdentifier());
        SchedulePlan plan = schedulePlanService.createSchedulePlan(planForm);
        return createdResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result getSchedulePlan(String guid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        
        SchedulePlan plan = schedulePlanService.getSchedulePlan(study, guid);
        return okResult(plan);
    }

    public Result updateSchedulePlan(String guid) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        DynamoSchedulePlan planForm = DynamoSchedulePlan.fromJson(requestToJSON(request()));
        planForm.setStudyKey(study.getIdentifier());
        SchedulePlan plan = schedulePlanService.updateSchedulePlan(planForm);
        
        return okResult(new GuidVersionHolder(plan.getGuid(), plan.getVersion()));
    }

    public Result deleteSchedulePlan(String guid) {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);

        schedulePlanService.deleteSchedulePlan(study, guid);
        return okResult("Schedule plan deleted.");
    }

}
