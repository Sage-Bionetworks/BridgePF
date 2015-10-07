package org.sagebionetworks.bridge.play.controllers;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.google.common.collect.Lists;

@Controller
public class ScheduleController extends BaseController {

    private SchedulePlanService schedulePlanService;
    
    @Autowired
    public void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    @Deprecated
    public Result getSchedulesV1() throws Exception {
        getAuthenticatedAndConsentedSession();
        return okResult(Collections.emptyList());
    }
    
    public Result getSchedules() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        ClientInfo clientInfo = getClientInfoFromUserAgentHeader();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(clientInfo, studyId);
        List<Schedule> schedules = Lists.newArrayListWithCapacity(plans.size());
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(session.getStudyIdentifier(), plan, session.getUser());
            schedules.add(schedule);
        }
        return okResult(schedules);
    }
    
}
