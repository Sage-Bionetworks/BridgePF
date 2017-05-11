package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.mvc.Result;

@Controller
public class ScheduledActivityController extends BaseController {
    
    private static final TypeReference<ArrayList<ScheduledActivity>> scheduledActivityTypeRef = new TypeReference<ArrayList<ScheduledActivity>>() {};

    private ScheduledActivityService scheduledActivityService;

    @Autowired
    public void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    
    // This annotation adds a deprecation header to the REST API method.
    @Deprecated
    public Result getTasks(String untilString, String offset, String daysAhead) throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(untilString, offset, daysAhead, null);
        
        return okResultAsTasks(scheduledActivities);
    }

    public Result getActivityHistory(String activityGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetBy, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = scheduledActivityService.getActivityHistory(
                session.getHealthCode(), activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy, pageSize);
        
        return ok(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page));
    }
    
    public Result getScheduledActivities(String untilString, String offset, String daysAhead, String minimumPerScheduleString)
            throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(untilString, offset, daysAhead,
                minimumPerScheduleString);
        
        return ok(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER
                .writeValueAsString(new ResourceList<ScheduledActivity>(scheduledActivities)));
    }
    
    public Result getScheduledActivitiesByDateRange(String startsOnString, String endsOnString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        DateTime startsOn = BridgeUtils.getDateTimeOrDefault(startsOnString, null);
        DateTime endsOn = BridgeUtils.getDateTimeOrDefault(endsOnString, null);
        if (startsOn == null || endsOn == null) {
            throw new BadRequestException("Both startsOn and endsOn are required and must be ISO 8601 timestamps.");
        }
        if (!startsOn.getZone().equals(endsOn.getZone())) {
            throw new BadRequestException("Both startsOn and endsOn timezone offsets must be the same.");
        }
        DateTimeZone requestTimeZone = startsOn.getZone();
        
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, startsOn, endsOn, 0);

        List<ScheduledActivity> scheduledActivities = scheduledActivityService.getScheduledActivitiesV4(context);
        
        return ok(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER
                .writeValueAsString(new ResourceList<ScheduledActivity>(scheduledActivities)));
    }

    public Result updateScheduledActivities() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        List<ScheduledActivity> scheduledActivities = MAPPER.convertValue(requestToJSON(request()),
                scheduledActivityTypeRef);
        scheduledActivityService.updateScheduledActivities(session.getHealthCode(), scheduledActivities);

        return okResult("Activities updated.");
    }

    <T> Result okResultAsTasks(List<T> list) {
        JsonNode node = MAPPER.valueToTree(new ResourceList<T>(list));
        ArrayNode items = (ArrayNode)node.get("items");
        for (int i=0; i < items.size(); i++) {
            ObjectNode object = (ObjectNode)items.get(i);
            object.put("type", "Task");
            object.remove("healthCode");
            object.remove("schedulePlanGuid");
        }
        return ok(node);
    }
    
    private List<ScheduledActivity> getScheduledActivitiesInternalV3(String untilString, String offset,
            String daysAhead, String minimumPerScheduleString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        DateTime endsOn = null;
        DateTimeZone requestTimeZone = null;
        int minimumPerSchedule = BridgeUtils.getIntOrDefault(minimumPerScheduleString, 0);

        if (StringUtils.isNotBlank(untilString)) {
            // Old API, infer time zone from the until parameter. This is not ideal.
            endsOn = DateTime.parse(untilString);
            requestTimeZone = endsOn.getZone();
        } else if (StringUtils.isNotBlank(daysAhead) && StringUtils.isNotBlank(offset)) {
            int numDays = Integer.parseInt(daysAhead);
            requestTimeZone = DateUtils.parseZoneFromOffsetString(offset);
            // When querying for days, we ignore the time of day of the request and query to then end of the day.
            endsOn = DateTime.now(requestTimeZone).plusDays(numDays).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            throw new BadRequestException("Supply either 'until' parameter, or 'daysAhead' parameter.");
        }
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, DateTime.now(), endsOn, minimumPerSchedule);
        return scheduledActivityService.getScheduledActivities(context);
    }
    
    private ScheduleContext getScheduledActivitiesInternal(UserSession session, DateTimeZone requestTimeZone,
            DateTime startsOn, DateTime endsOn, int minPerSchedule) {
        ScheduleContext.Builder builder = new ScheduleContext.Builder();
        
        // This time zone is the time zone of the user upon first contacting the server for activities, and
        // ensures that events are scheduled in this time zone. This ensures that a user will receive activities 
        // on the day they contact the server. If it has not yet been captured, this is the first request, 
        // capture and persist it.
        DateTimeZone initialTimeZone = session.getParticipant().getTimeZone();
        if (initialTimeZone == null) {
            initialTimeZone = persistTimeZone(session, requestTimeZone);
        }
        
        builder.withStartsOn(startsOn);
        builder.withEndsOn(endsOn);
        builder.withInitialTimeZone(initialTimeZone);
        builder.withUserDataGroups(session.getParticipant().getDataGroups());
        builder.withHealthCode(session.getHealthCode());
        builder.withUserId(session.getId());
        builder.withStudyIdentifier(session.getStudyIdentifier());
        builder.withAccountCreatedOn(session.getParticipant().getCreatedOn());
        builder.withLanguages(getLanguages(session));
        builder.withClientInfo(getClientInfoFromUserAgentHeader());
        builder.withMinimumPerSchedule(minPerSchedule);
        
        ScheduleContext context = builder.build();
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserId(context.getCriteriaContext().getUserId())
                .withClientInfo(context.getCriteriaContext().getClientInfo())
                .withUserAgent(request().getHeader(USER_AGENT))
                .withLanguages(context.getCriteriaContext().getLanguages())
                .withUserDataGroups(context.getCriteriaContext().getUserDataGroups())
                .withActivitiesAccessedOn(DateUtils.getCurrentDateTime().withZone(requestTimeZone))
                .withTimeZone(context.getInitialTimeZone())
                .withStudyIdentifier(context.getCriteriaContext().getStudyIdentifier()).build();
        cacheProvider.updateRequestInfo(requestInfo);
        
        return context;
    }

    private DateTimeZone persistTimeZone(UserSession session, DateTimeZone timeZone) {
        optionsService.setDateTimeZone(session.getStudyIdentifier(), session.getHealthCode(),
                ParticipantOption.TIME_ZONE, timeZone);
        
        sessionUpdateService.updateTimeZone(session, timeZone);
        
        return timeZone;
    }
}
