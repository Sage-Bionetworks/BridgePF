package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
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
    private static final String MISSING_TIMESTAMP_ERROR = "startsOn and endsOn are both required and must be ISO 8601 timestamps.";
    private static final String AMBIGUOUS_TIMEZONE_ERROR = "startsOn and endsOn must be in the same time zone.";

    private ScheduledActivityService scheduledActivityService;

    @Autowired
    public void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    
    @Deprecated
    public Result getTasks(String untilString, String offset, String daysAhead) throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(untilString, offset, daysAhead, null);
        
        return okResultAsTasks(scheduledActivities);
    }
    
    @Deprecated
    public Result getScheduledActivities(String untilString, String offset, String daysAhead, String minimumPerScheduleString)
            throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternalV3(untilString, offset, daysAhead,
                minimumPerScheduleString);
        
        return okResult(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER,
                new ResourceList<ScheduledActivity>(scheduledActivities));
    }

    public Result getActivityHistory(String activityGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetBy, String offsetKey, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        if (offsetKey == null) {
            offsetKey = offsetBy;
        }
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = scheduledActivityService.getActivityHistory(
                session.getHealthCode(), activityGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);

        // If offsetBy was supplied, we return it as a top-level property of the list for backwards compatibility.
        String json = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
        ObjectNode node = (ObjectNode)MAPPER.readTree(json);
        if (offsetBy != null) {
            node.put(OFFSET_BY, offsetBy);    
        }
        return ok(node);
    }
    
    public Result getActivityHistoryV3(String activityTypeString, String referentGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetKey, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        ActivityType activityType = ActivityType.fromPlural(activityTypeString);
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = scheduledActivityService.getActivityHistory(
                session.getHealthCode(), activityType, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey,
                pageSize);
        
        return okResult(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER, page);
    }
    
    public Result getScheduledActivitiesByDateRange(String startTimeString, String endTimeString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DateTime startsOn = BridgeUtils.getDateTimeOrDefault(startTimeString, null);
        DateTime endsOn = BridgeUtils.getDateTimeOrDefault(endTimeString, null);
        if (startsOn == null || endsOn == null) {
            throw new BadRequestException(MISSING_TIMESTAMP_ERROR);
        }
        if (!startsOn.getZone().equals(endsOn.getZone())) {
            throw new BadRequestException(AMBIGUOUS_TIMEZONE_ERROR);
        }
        DateTime startsOnInclusive = startsOn.minusMillis(1);

        DateTimeZone requestTimeZone = startsOn.getZone();
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, startsOnInclusive, endsOn, 0);

        List<ScheduledActivity> scheduledActivities = scheduledActivityService.getScheduledActivitiesV4(study, context);
        
        DateTimeRangeResourceList<ScheduledActivity> results = new DateTimeRangeResourceList<>(scheduledActivities)
                .withRequestParam(ResourceList.START_TIME, startsOn)
                .withRequestParam(ResourceList.END_TIME, endsOn);
        return okResult(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER, results);
    }

    public Result updateScheduledActivities() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        List<ScheduledActivity> scheduledActivities = MAPPER.convertValue(
                parseJson(request(), JsonNode.class), scheduledActivityTypeRef);
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
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
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
        DateTime now = DateTime.now(requestTimeZone);
        ScheduleContext context = getScheduledActivitiesInternal(session, requestTimeZone, now, endsOn, minimumPerSchedule);
        return scheduledActivityService.getScheduledActivities(study, context);
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
        builder.withUserSubstudyIds(session.getParticipant().getSubstudyIds());
        builder.withHealthCode(session.getHealthCode());
        builder.withUserId(session.getId());
        builder.withStudyIdentifier(session.getStudyIdentifier());
        builder.withAccountCreatedOn(session.getParticipant().getCreatedOn());
        builder.withLanguages(getLanguages(session));
        builder.withClientInfo(getClientInfoFromUserAgentHeader());
        builder.withMinimumPerSchedule(minPerSchedule);
        
        ScheduleContext context = builder.build();
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withActivitiesAccessedOn(DateUtils.getCurrentDateTime().withZone(requestTimeZone))
                .build();
        cacheProvider.updateRequestInfo(requestInfo);
        
        return context;
    }

    private DateTimeZone persistTimeZone(UserSession session, DateTimeZone timeZone) {
        accountDao.editAccount(session.getStudyIdentifier(), session.getHealthCode(),
                account -> account.setTimeZone(timeZone));
        sessionUpdateService.updateTimeZone(session, timeZone);
        return timeZone;
    }
}
