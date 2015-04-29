package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.google.common.collect.Lists;

@Controller("scheduleController")
public class ScheduleController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    @Autowired
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    public Result getSchedules() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        Study study = studyService.getStudy(studyId);
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(studyId);
        List<Schedule> schedules = Lists.newArrayListWithCapacity(plans.size());
        for (SchedulePlan plan : plans) {
            // Cast seems unnecessary, but we are getting NoSuchMethodError when deployed
            Schedule schedule = plan.getStrategy().getScheduleForUser((StudyIdentifier)study, plan, session.getUser());
            schedules.add(schedule);
        }
        return okResult(schedules);
    }
    
}
