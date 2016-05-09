package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.play.controllers.ScheduledActivityController;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Result;
import play.test.Helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ScheduledActivityControllerTest {

    private static final DateTime ACCOUNT_CREATED_ON = DateTime.now();
    
    private static final String ID = "id";
    
    private ScheduledActivityController controller;
    
    private ClientInfo clientInfo;

    @Mock
    ScheduledActivityService scheduledActivityService;
    
    @Mock
    AccountDao accountDao;
    
    @Mock
    StudyService studyService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Mock
    Study study;
    
    @Mock
    Account account;
    
    @Captor
    ArgumentCaptor<ScheduleContext> contextCaptor;
    
    UserSession session;
    
    @Before
    public void before() throws Exception {
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setScheduledOn(DateTime.now(DateTimeZone.UTC).minusDays(1));
        schActivity.setActivity(TestConstants.TEST_3_ACTIVITY);
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        TestUtils.mockPlayContextWithJson(json);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withDataGroups(Sets.newHashSet("group1"))
                .withLanguages(TestUtils.newLinkedHashSet("en","fr"))
                .withCreatedOn(ACCOUNT_CREATED_ON)
                .withId(ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        when(scheduledActivityService.getScheduledActivities(any(User.class), any(ScheduleContext.class))).thenReturn(list);

        doReturn(ACCOUNT_CREATED_ON).when(account).getCreatedOn();
        doReturn(account).when(accountDao).getAccount(any(), eq(ID));
        doReturn(study).when(studyService).getStudy(TestConstants.TEST_STUDY_IDENTIFIER);

        controller = spy(new ScheduledActivityController());
        controller.setScheduledActivityService(scheduledActivityService);
        controller.setStudyService(studyService);
        controller.setCacheProvider(cacheProvider);
        controller.setAccountDao(accountDao);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        clientInfo = ClientInfo.fromUserAgentCache("App Name/4 SDK/2");
        doReturn(clientInfo).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @Test
    public void getScheduledActivtiesAssemblesCorrectContext() throws Exception {
        ArgumentCaptor<ScheduleContext> captor = ArgumentCaptor.forClass(ScheduleContext.class);
        
        List<ScheduledActivity> list = Lists.newArrayList();
        scheduledActivityService = mock(ScheduledActivityService.class);
        when(scheduledActivityService.getScheduledActivities(any(User.class), any(ScheduleContext.class))).thenReturn(list);
        controller.setScheduledActivityService(scheduledActivityService);
        
        controller.getScheduledActivities(null, "+03:00", "3");
        
        verify(scheduledActivityService).getScheduledActivities(any(User.class), captor.capture());
        
        ScheduleContext context = captor.getValue();
        assertEquals(DateTimeZone.forOffsetHours(3), context.getZone());
        assertEquals(Sets.newHashSet("group1"), context.getCriteriaContext().getUserDataGroups());
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals("BBB", critContext.getHealthCode());
        assertEquals(TestUtils.newLinkedHashSet("en","fr"), critContext.getLanguages());
        assertEquals("api", critContext.getStudyIdentifier().getIdentifier());
        assertEquals(clientInfo, critContext.getClientInfo());
    }
    
    @Test
    public void getScheduledActivitiesAsScheduledActivitiesReturnsCorrectType() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getScheduledActivities(now.toString(), null, null);
        String output = Helpers.contentAsString(result);

        JsonNode results = BridgeObjectMapper.get().readTree(output);
        ArrayNode items = (ArrayNode)results.get("items");
        for (int i=0; i < items.size(); i++) {
            assertEquals("ScheduledActivity", items.get(i).get("type").asText());
        }
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitesAsTasks() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getTasks(now.toString(), null, null);
        String output = Helpers.contentAsString(result);
        
        // Verify that even without the writer, we are not leaking these values
        // through the API, and they are typed as "Task"s.
        JsonNode items = BridgeObjectMapper.get().readTree(output).get("items");
        assertTrue(items.size() > 0);
        for (int i=0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            assertNotNull(item.get("guid"));
            assertNull(item.get("healthCode"));
            assertNull(item.get("schedulePlanGuid"));
            assertEquals("Task", item.get("type").asText());
        }
    }
    
    @Test
    public void getScheduledActivitiesWithUntil() throws Exception {
        // Until value is simply passed along as is to the scheduler.
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        controller.getScheduledActivities(now.toString(), null, null);
        verify(scheduledActivityService).getScheduledActivities(any(User.class), contextCaptor.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(now, contextCaptor.getValue().getEndsOn());
        assertEquals(now.getZone(), contextCaptor.getValue().getZone());
    }
    
    @Test
    public void getScheduledActivitiesWithDaysAheadAndTimeZone() throws Exception {
        // We expect the endsOn value to be three days from now at the end of the day 
        // (set millis to 0 so the values match at the end of the test).
        DateTime expectedEndsOn = DateTime.now()
            .withZone(DateTimeZone.forOffsetHours(3)).plusDays(3)
            .withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(0);
        
        controller.getScheduledActivities(null, "+03:00", "3");
        verify(scheduledActivityService).getScheduledActivities(any(User.class), contextCaptor.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(expectedEndsOn, contextCaptor.getValue().getEndsOn().withMillisOfSecond(0));
        assertEquals(expectedEndsOn.getZone(), contextCaptor.getValue().getZone());
        assertEquals(clientInfo, contextCaptor.getValue().getCriteriaContext().getClientInfo());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        controller.updateScheduledActivities();
        verify(scheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(scheduledActivityService);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new ScheduledActivityController();
        controller.getScheduledActivities(DateTime.now().toString(), null, null);
    }
    
    @Test
    public void fullyInitializedSessionProvidesAccountCreatedOnInScheduleContext() throws Exception {
        controller.getScheduledActivities(null, "-07:00", "3");
        verify(scheduledActivityService).getScheduledActivities(any(), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(ACCOUNT_CREATED_ON, context.getAccountCreatedOn());
    }
    
    @Test
    public void oldSessionsWithIdAndNoAccountCreatedOn() throws Exception {
        session.getUser().setAccountCreatedOn(null); // this is not currently in the session
        
        controller.getScheduledActivities(null, "-07:00", "3");
        verify(scheduledActivityService).getScheduledActivities(any(), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(ACCOUNT_CREATED_ON, context.getAccountCreatedOn());
    }
    
    @Test
    public void oldSessionsWithNoIdAndNoAccountCreatedOn() throws Exception {
        session.getUser().setAccountCreatedOn(null); // these are not currently in the session
        session.getUser().setId(null);
        
        controller.getScheduledActivities(null, "-07:00", "3");
        verify(scheduledActivityService).getScheduledActivities(any(), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertNotNull(context.getAccountCreatedOn()); // this is a timestamp, so
    }
}
