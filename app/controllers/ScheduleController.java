package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import play.mvc.Result;

import com.google.common.collect.Lists;

public class ScheduleController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    public Result getSchedules() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(getStudyIdentifier());
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(study);
        List<Schedule> schedules = Lists.newArrayListWithCapacity(plans.size());
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(study, plan, session.getUser());
            schedules.add(schedule);
        }
        return okResult(schedules);
    }
    
}
