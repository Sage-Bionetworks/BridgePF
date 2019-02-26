package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.BodyParser;
import play.mvc.Result;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.UserAdminService;

@Controller
public class ParticipantController extends BaseController {
    
    private static final String NOTIFY_SUCCESS_MESSAGE = "Message has been sent to external notification service.";

    private ParticipantService participantService;
    
    private UserAdminService userAdminService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /** Researcher API to allow backfill of SMS notification registrations. */
    @BodyParser.Of(BodyParser.Empty.class)
    public Result createSmsRegistration(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.createSmsRegistration(study, userId);
        return createdResult("SMS notification registration created");
    }

    public Result getSelfParticipant() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, session.getId(), false);
        
        String ser = StudyParticipant.API_NO_HEALTH_CODE_WRITER.writeValueAsString(participant);
        
        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result updateSelfParticipant() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        // By copying only values that were included in the JSON onto the existing StudyParticipant,
        // we allow clients to only send back partial JSON to update the user. This has been the 
        // usage pattern in prior APIs and it will make refactoring to use this API easier.
        JsonNode node = parseJson(request(), JsonNode.class);
        Set<String> fieldNames = Sets.newHashSet(node.fieldNames());

        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);
        StudyParticipant existing = participantService.getParticipant(study, session.getId(), false);
        StudyParticipant updated = new StudyParticipant.Builder()
                .copyOf(existing)
                .copyFieldsOf(participant, fieldNames)
                .withId(session.getId()).build();
        participantService.updateParticipant(study, updated);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(getClientInfoFromUserAgentHeader())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(updated.getDataGroups())
                .withUserSubstudyIds(updated.getSubstudyIds())
                .withStudyIdentifier(session.getStudyIdentifier())
                .build();
        
        sessionUpdateService.updateParticipant(session, context, updated);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result deleteTestParticipant(String userId) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        AccountId accountId = AccountId.forId(study.getIdentifier(), userId);
        
        StudyParticipant participant = participantService.getParticipant(study, accountId, false);
        if (!participant.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP)) {
            throw new UnauthorizedException("Account is not a test account.");
        }
        userAdminService.deleteUser(study, userId);
        
        return okResult("User deleted.");
    }
    
    public Result getActivityEventsForWorker(String studyId, String userId) {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);

        return okResult(participantService.getActivityEvents(study, userId));
    }
    
    public Result getActivityHistoryForWorkerV3(String studyId, String userId, String activityType, String referentGuid,
            String scheduledOnStart, String scheduledOnEnd, String offsetKey, String pageSize) throws Exception {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);
        
        return getActivityHistoryV3Internal(study, userId, activityType, referentGuid, scheduledOnStart, scheduledOnEnd,
                offsetKey, pageSize);
    }
    
    public Result getActivityHistoryForWorkerV2(String studyId, String userId, String activityGuid,
            String scheduledOnStart, String scheduledOnEnd, String offsetBy, String offsetKey, String pageSize)
            throws Exception {
        getAuthenticatedSession(Roles.WORKER);
        Study study = studyService.getStudy(studyId);
        
        return getActivityHistoryInternalV2(study, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy,
                offsetKey, pageSize);
    }
    
    public Result updateIdentifiers() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        IdentifierUpdate update = parseJson(request(), IdentifierUpdate.class);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        CriteriaContext context = getCriteriaContext(session);
        
        StudyParticipant participant = participantService.updateIdentifiers(study, context, update);
        sessionUpdateService.updateParticipant(session, context, participant);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    @Deprecated
    public Result getParticipants(String offsetByString, String pageSizeString, String emailFilter, String phoneFilter,
            String startDateString, String endDateString, String startTimeString,
            String endTimeString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return getParticipantsInternal(study, offsetByString, pageSizeString, emailFilter, phoneFilter, startDateString,
                endDateString, startTimeString, endTimeString);
    }

    public Result searchForAccountSummaries() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        AccountSummarySearch search = parseJson(request(), AccountSummarySearch.class);
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, search);
        
        return okResult(page);
    }
    
    @Deprecated
    public Result getParticipantsForWorker(String studyId, String offsetByString, String pageSizeString,
            String emailFilter, String phoneFilter, String startDateString, String endDateString,
            String startTimeString, String endTimeString) {
        getAuthenticatedSession(WORKER);
        
        Study study = studyService.getStudy(studyId);
        return getParticipantsInternal(study, offsetByString, pageSizeString, emailFilter, phoneFilter, startDateString,
                endDateString, startTimeString, endTimeString);
    }
    
    public Result searchForAccountSummariesForWorker(String studyId) throws Exception {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);
        
        AccountSummarySearch search = parseJson(request(), AccountSummarySearch.class);
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, search);
        
        return okResult(page);
    }
    
    public Result createParticipant() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        IdentifierHolder holder = participantService.createParticipant(study, participant, true);
        return createdResult(holder);
    }
    
    public Result getParticipant(String userId, boolean consents) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        AccountId accountId = BridgeUtils.parseAccountId(study.getIdentifier(), userId);
        StudyParticipant participant = participantService.getParticipant(study, accountId, consents);

        ObjectWriter writer = (study.isHealthCodeExportEnabled()) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        String ser = writer.writeValueAsString(participant);

        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result getParticipantForWorker(String studyId, String userId, boolean consents) throws Exception {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);

        AccountId accountId = BridgeUtils.parseAccountId(studyId, userId);
        StudyParticipant participant = participantService.getParticipant(study, accountId, consents);
        
        ObjectWriter writer = StudyParticipant.API_WITH_HEALTH_CODE_WRITER;
        String ser = writer.writeValueAsString(participant);

        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result getRequestInfo(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Verify it's in the same study as the researcher.
        RequestInfo requestInfo = cacheProvider.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        } else if (!study.getStudyIdentifier().equals(requestInfo.getStudyIdentifier())) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }
        return okResult(requestInfo);
    }
    
    public Result updateParticipant(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
 
        // Force userId of the URL
        participant = new StudyParticipant.Builder().copyOf(participant).withId(userId).build();
        
        participantService.updateParticipant(study, participant);

        return okResult("Participant updated.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result signOut(String userId, boolean deleteReauthToken) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.signUserOut(study, userId, deleteReauthToken);

        return okResult("User signed out.");
    }

    @BodyParser.Of(BodyParser.Empty.class)
    public Result requestResetPassword(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.requestResetPassword(study, userId);
        
        return okResult("Request to reset password sent to user.");
    }
    
    public Result getActivityHistoryV2(String userId, String activityGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetBy, String offsetKey, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        return getActivityHistoryInternalV2(study, userId, activityGuid, scheduledOnStartString,
            scheduledOnEndString, offsetBy, offsetKey, pageSizeString);
    }
    
    public Result getActivityHistoryV3(String userId, String activityTypeString, String referentGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetKey, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return getActivityHistoryV3Internal(study, userId, activityTypeString, referentGuid, scheduledOnStartString,
                scheduledOnEndString, offsetKey, pageSizeString);
    }
    
    public Result deleteActivities(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.deleteActivities(study, userId);
        
        return okResult("Scheduled activities deleted.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result resendEmailVerification(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.resendVerification(study, ChannelType.EMAIL, userId);
        
        return okResult("Email verification request has been resent to user.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result resendPhoneVerification(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.resendVerification(study, ChannelType.PHONE, userId);
        
        return okResult("Phone verification request has been resent to user.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result resendConsentAgreement(String userId, String subpopulationGuid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(subpopulationGuid);
        participantService.resendConsentAgreement(study, subpopGuid, userId);
        
        return okResult("Consent agreement resent to user.");
    }
    
    public Result withdrawFromStudy(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawFromStudy(study, userId, withdrawal, withdrewOn);
        
        return okResult("User has been withdrawn from the study.");
    }
    
    public Result withdrawConsent(String userId, String subpopulationGuid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(subpopulationGuid);
        
        participantService.withdrawConsent(study, userId, subpopGuid, withdrawal, withdrewOn);
        
        return okResult("User has been withdrawn from subpopulation '"+subpopulationGuid+"'.");
    }
    
    public Result getUploads(String userId, String startTimeString, String endTimeString, Integer pageSize,
            String offsetKey) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);

        ForwardCursorPagedResourceList<UploadView> uploads = participantService.getUploads(
                study, userId, startTime, endTime, pageSize, offsetKey);

        return okResult(uploads);
    }
    
    public Result getNotificationRegistrations(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        List<NotificationRegistration> registrations = participantService.listRegistrations(study, userId);
        
        return okResult(registrations);
    }
    
    public Result sendNotification(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        NotificationMessage message = parseJson(request(), NotificationMessage.class);
        
        Set<String> erroredNotifications = participantService.sendNotification(study, userId, message);
        
        if (erroredNotifications.isEmpty()) {
            return acceptedResult(NOTIFY_SUCCESS_MESSAGE);                    
        }
        return acceptedResult(NOTIFY_SUCCESS_MESSAGE + " Some registrations returned errors: "
                + BridgeUtils.COMMA_SPACE_JOINER.join(erroredNotifications) + ".");
    }

    public Result getActivityEvents(String userId) {
        UserSession researcherSession = getAuthenticatedSession(Roles.RESEARCHER);
        Study study = studyService.getStudy(researcherSession.getStudyIdentifier());

        return okResult(participantService.getActivityEvents(study, userId));
    }
    
    public Result sendSmsMessage(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        SmsTemplate template = parseJson(request(), SmsTemplate.class);
        
        participantService.sendSmsMessage(study, userId, template);
        return acceptedResult("Message sent.");
    }

    public Result sendSmsMessageForWorker(String studyId, String userId) {
        getAuthenticatedSession(WORKER);
        Study study = studyService.getStudy(studyId);
        SmsTemplate template = parseJson(request(), SmsTemplate.class);
        
        participantService.sendSmsMessage(study, userId, template);
        return acceptedResult("Message sent.");
    }

    private Result getParticipantsInternal(Study study, String offsetByString, String pageSizeString,
            String emailFilter, String phoneFilter, String startDateString, String endDateString,
            String startTimeString, String endTimeString) {
        
        int offsetBy = getIntOrDefault(offsetByString, 0);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);
        
        // For naming consistency, we are changing from the user of startDate/endDate to startTime/endTime
        // for DateTime parameters. Both are accepted by these participant API endpoints (the only places 
        // where this needed to change).
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        if (startTime == null) {
            startTime = getDateTimeOrDefault(startDateString, null);
        }
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        if (endTime == null) {
            endTime = getDateTimeOrDefault(endDateString, null);
        }
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(offsetBy)
                .withPageSize(pageSize)
                .withEmailFilter(emailFilter)
                .withPhoneFilter(phoneFilter)
                .withStartTime(startTime)
                .withEndTime(endTime).build();
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, search);
        
        // Similarly, we will return startTime/endTime in the top-level request parameter properties as 
        // startDate/endDate while transitioning, to maintain backwards compatibility.
        ObjectNode node = MAPPER.valueToTree(page);
        Map<String,Object> rp = page.getRequestParams();
        if (rp.get(START_TIME) != null) {
            node.put(START_DATE, (String)rp.get(START_TIME));    
        }
        if (rp.get(END_TIME) != null) {
            node.put(END_DATE, (String)rp.get(END_TIME));    
        }
        return ok(node);
    }
    
    private Result getActivityHistoryInternalV2(Study study, String userId, String activityGuid,
            String scheduledOnStartString, String scheduledOnEndString, String offsetBy, String offsetKey,
            String pageSizeString) throws Exception {
        if (offsetKey == null) {
            offsetKey = offsetBy;
        }
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(
                study, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);

        // If offsetBy was supplied, we return it as a top-level property of the list for backwards compatibility.
        String json = ScheduledActivity.RESEARCHER_SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page);
        ObjectNode node = (ObjectNode)MAPPER.readTree(json);
        if (offsetBy != null) {
            node.put(OFFSET_BY, offsetBy);    
        }
        return ok(node);
    }
    
    private Result getActivityHistoryV3Internal(Study study, String userId, String activityTypeString,
            String referentGuid, String scheduledOnStartString, String scheduledOnEndString, String offsetKey,
            String pageSizeString) throws Exception {
        
        ActivityType activityType = ActivityType.fromPlural(activityTypeString);
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(study, userId,
                activityType, referentGuid, scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
        
        return okResult(ScheduledActivity.SCHEDULED_ACTIVITY_WRITER, page);
    }
}
