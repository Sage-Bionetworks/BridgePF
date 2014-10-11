package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.services.ScheduleService;

import play.mvc.Result;

public class ScheduleController extends BaseController {

    private ScheduleService scheduleService;
    
    public void setScheduleService(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    public Result getSchedules() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        
        List<Schedule> schedules = scheduleService.getSchedules(study, session.getUser());
                
        return okResult(schedules);
    }
    
}
