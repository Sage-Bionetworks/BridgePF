package org.sagebionetworks.bridge.play.controllers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
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

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledActivityController.class);  
    
    private static final TypeReference<ArrayList<ScheduledActivity>> scheduledActivityTypeRef = new TypeReference<ArrayList<ScheduledActivity>>() {};

    private ScheduledActivityService scheduledActivityService;
    
    private AccountDao accountDao;

    @Autowired
    public void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    
    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    // This annotation adds a deprecation header to the REST API method.
    @Deprecated
    public Result getTasks(String untilString, String offset, String daysAhead) throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternal(untilString, offset, daysAhead);
        
        return okResultAsTasks(scheduledActivities);
    }

    public Result getScheduledActivities(String untilString, String offset, String daysAhead) throws Exception {
        List<ScheduledActivity> scheduledActivities = getScheduledActivitiesInternal(untilString, offset, daysAhead);
        
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
    
    private List<ScheduledActivity> getScheduledActivitiesInternal(String untilString, String offset, String daysAhead)
            throws Exception {
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
            endsOn = DateTime.now(zone).plusDays(numDays).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            throw new BadRequestException("Supply either 'until' parameter, or 'daysAhead' and 'offset' parameters.");
        }
        ClientInfo clientInfo = getClientInfoFromUserAgentHeader();
        
        DateTime accountCreatedOn = session.getStudyParticipant().getCreatedOn();
        if (accountCreatedOn == null) {
            Study study = studyService.getStudy(session.getStudyIdentifier());
            // Everyone should have an ID at this point... otherwise sessions are hanging out for over a week.
            if (session.getId() == null) {
                accountCreatedOn = DateTime.now();
                LOG.debug("neither accountCreatedOn nor ID exist in session, using current time");
            } else {
                Account account = accountDao.getAccount(study, session.getId());
                accountCreatedOn = account.getCreatedOn();
                LOG.debug("accountCreatedOn not in session, retrieving it and updating session");
            }
            StudyParticipant participant = new StudyParticipant.Builder().copyOf(session.getStudyParticipant())
                    .withCreatedOn(accountCreatedOn).build();
            session.setStudyParticipant(participant);
            updateSession(session);
        }
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withLanguages(getLanguages(session))
                .withUserDataGroups(session.getStudyParticipant().getDataGroups())
                .withHealthCode(session.getHealthCode())
                .withStudyIdentifier(session.getStudyIdentifier())
                .withClientInfo(clientInfo)
                .withTimeZone(zone)
                .withAccountCreatedOn(accountCreatedOn)
                .withEndsOn(endsOn).build();
        return scheduledActivityService.getScheduledActivities(context);
    }
}
