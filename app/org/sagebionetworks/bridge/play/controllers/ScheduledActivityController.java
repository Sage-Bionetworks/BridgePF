package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
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
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternal(untilString, offset, daysAhead, null);
        
        return okResultAsTasks(scheduledActivities);
    }

    public Result getScheduledActivities(String untilString, String offset, String daysAhead, String minimumPerScheduleString)
            throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternal(untilString, offset, daysAhead, minimumPerScheduleString);
        
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
    
    private List<ScheduledActivity> getScheduledActivitiesInternal(String untilString, String offset, String daysAhead,
            String minimumPerScheduleString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        ScheduleContext.Builder builder = new ScheduleContext.Builder();
        DateTimeZone zone = addEndsOnWithZone(builder, untilString, offset, daysAhead);

        DateTimeZone timeZone = session.getParticipant().getTimeZone();
        if (timeZone == null) {
            timeZone = persistTimeZone(session, zone);
        }

        builder.withTimeZone(timeZone);
        builder.withUserDataGroups(session.getParticipant().getDataGroups());
        builder.withHealthCode(session.getHealthCode());
        builder.withUserId(session.getId());
        builder.withStudyIdentifier(session.getStudyIdentifier());
        builder.withAccountCreatedOn(session.getParticipant().getCreatedOn());
        builder.withLanguages(getLanguages(session));
        builder.withClientInfo(getClientInfoFromUserAgentHeader());
        builder.withMinimumPerSchedule(getIntOrDefault(minimumPerScheduleString, 0));
        
        ScheduleContext context = builder.build();
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserId(context.getCriteriaContext().getUserId())
                .withClientInfo(context.getCriteriaContext().getClientInfo())
                .withUserAgent(request().getHeader(USER_AGENT))
                .withLanguages(context.getCriteriaContext().getLanguages())
                .withUserDataGroups(context.getCriteriaContext().getUserDataGroups())
                .withActivitiesAccessedOn(context.getNow())
                .withTimeZone(context.getZone())
                .withStudyIdentifier(context.getCriteriaContext().getStudyIdentifier()).build();
        cacheProvider.updateRequestInfo(requestInfo);

        return scheduledActivityService.getScheduledActivities(context);
    }

    private DateTimeZone persistTimeZone(UserSession session, DateTimeZone timeZone) {
        optionsService.setDateTimeZone(session.getStudyIdentifier(), session.getHealthCode(),
                ParticipantOption.TIME_ZONE, timeZone);
        
        StudyParticipant updatedParticipant = session.getParticipant();
        session.setParticipant(new StudyParticipant.Builder()
                .copyOf(updatedParticipant)
                .withTimeZone(timeZone).build());
        updateSession(session);
        
        return timeZone;
    }
    
    private DateTimeZone addEndsOnWithZone(ScheduleContext.Builder builder, String untilString, String offset, String daysAhead) {
        DateTime endsOn = null;
        DateTimeZone zone = null;

        if (StringUtils.isNotBlank(untilString)) {
            // Old API, infer time zone from the until parameter. This is not ideal.
            endsOn = DateTime.parse(untilString);
            zone = endsOn.getZone();
        } else if (StringUtils.isNotBlank(daysAhead) && StringUtils.isNotBlank(offset)) {
            int numDays = Integer.parseInt(daysAhead);
            zone = DateUtils.parseZoneFromOffsetString(offset);
            // When querying for days, we ignore the time of day of the request and query to then end of the day.
            endsOn = DateTime.now(zone).plusDays(numDays).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            throw new BadRequestException("Supply either 'until' parameter, or 'daysAhead' parameter.");
        }
        builder.withEndsOn(endsOn);
        return zone;
    }
}
