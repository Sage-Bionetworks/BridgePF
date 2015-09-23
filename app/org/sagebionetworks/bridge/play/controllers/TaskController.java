package org.sagebionetworks.bridge.play.controllers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.services.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;

import play.mvc.Result;

@Controller
public class TaskController extends BaseController {

    private static final TypeReference<ArrayList<Task>> taskTypeRef = new TypeReference<ArrayList<Task>>() {};
    
    private static final BridgeObjectMapper mapper = BridgeObjectMapper.get();
    
    private TaskService taskService;
    
    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }
    
    public Result getTasks(String untilString, String offset, String daysAhead) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        DateTime endsOn = null;
        DateTimeZone zone = null;
        
        if (StringUtils.isNotBlank(untilString)) {
            // Old API, infer time zone from the until parameter. This is not ideal.
            endsOn = DateTime.parse(untilString);
            zone = endsOn.getZone();
        } else if (StringUtils.isNotBlank(daysAhead) && StringUtils.isNotBlank(offset)) {
            zone = DateUtils.parseZoneFromOffsetString(offset);
            int numDays = Integer.parseInt(daysAhead);
            // When querying for days, we ignore the time of day of the request and query to then end of the day.
            endsOn = DateTime.now(zone).plusDays(numDays)
                .withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            throw new BadRequestException("Supply either 'until' parameter, or 'daysAhead' and 'offset' parameters.");
        }
        ScheduleContext context = new ScheduleContext(zone, endsOn, session.getUser().getHealthCode(), null, null);
        List<Task> tasks = taskService.getTasks(session.getUser(), context);
        return okResult(tasks);
    }
    
    public Result updateTasks() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<Task> tasks = mapper.convertValue(requestToJSON(request()), taskTypeRef);
        taskService.updateTasks(session.getUser().getHealthCode(), tasks);
        
        return okResult("Tasks updated.");
    }
    
}
