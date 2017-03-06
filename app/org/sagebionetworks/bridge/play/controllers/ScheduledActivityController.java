package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.dao.ParticipantOption;
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
    
    private static final TypeReference<ArrayList<ScheduledActivity>> SCHEDULED_ACTIVITY_TYPE_REF = new TypeReference<ArrayList<ScheduledActivity>>() {};

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
                SCHEDULED_ACTIVITY_TYPE_REF);
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

        DateTime until = (isNotBlank(untilString)) ? DateTime.parse(untilString) : null;
        
        ScheduleContext.Builder builder = new ScheduleContext.Builder();
        DateTimeZone requestTimeZone = getRequestTimeZone(builder, until, offset, daysAhead);
        // The initial time zone is the time zone of the user upon first contacting the server for activities; events
        // are scheduled in this time zone. This ensures that a user will receive activities on the day they contact 
        // the server. If it has not yet been captured, this is the first request, so capture and persist the value.
        DateTimeZone initialTimeZone = session.getParticipant().getTimeZone();
        if (initialTimeZone == null) {
            initialTimeZone = persistTimeZone(session, requestTimeZone);
        }
        addEndsOn(builder, until, offset, daysAhead);
        builder.withInitialTimeZone(initialTimeZone);
        builder.withRequestTimeZone(requestTimeZone);
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
                .withTimeZone(context.getInitialTimeZone())
                .withStudyIdentifier(context.getCriteriaContext().getStudyIdentifier()).build();
        cacheProvider.updateRequestInfo(requestInfo);

        return scheduledActivityService.getScheduledActivities(context);
    }
    
    private DateTimeZone persistTimeZone(UserSession session, DateTimeZone timeZone) {
        optionsService.setDateTimeZone(session.getStudyIdentifier(), session.getHealthCode(),
                ParticipantOption.TIME_ZONE, timeZone);
        
        StudyParticipant participant = session.getParticipant();
        session.setParticipant(new StudyParticipant.Builder()
                .copyOf(participant)
                .withTimeZone(timeZone).build());
        updateSession(session);
        
        return timeZone;
    }

    private DateTimeZone getRequestTimeZone(ScheduleContext.Builder builder, DateTime until, String offset, String daysAhead) {
        DateTimeZone requestTimeZone = null;
        if (isNotBlank(offset)) {
            requestTimeZone = DateUtils.parseZoneFromOffsetString(offset);
        } else if (until != null) {
            requestTimeZone = until.getZone();
        }
        return requestTimeZone;
    }
    
    private void addEndsOn(ScheduleContext.Builder builder, DateTime until, String offset, String daysAhead) {
        // We've validated the parameters in addRequestTimeZone, we need not verify they exist again. 
        // ScheduleContext validation will fail if we cannot produce an endsOn value (originally provided 
        // through an "until" parameter, but now indicated with a "daysAhead" parameter).
        if (until != null) {
            builder.withEndsOn(until);
        } else {
            int numDays = Integer.parseInt(daysAhead);
            builder.withDaysAhead(numDays);
        }
    }}
