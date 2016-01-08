package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DynamoScheduledActivityDaoMockTest {

    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");

    private static final String HEALTH_CODE = "AAA";
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("mock-study");
    private static final DateTimeZone PACIFIC_TIME_ZONE = DateTimeZone.forOffsetHours(-7);
    
    private static final String BASE_URL = BridgeConfigFactory.getConfig().getWebservicesURL();
    private static final String ACTIVITY_1_REF = BASE_URL + "/v3/surveys/AAA/revisions/published";
    private static final String ACTIVITY_2_REF = BASE_URL + "/v3/surveys/BBB/revisions/published";
    private static final String ACTIVITY_3_REF = TestConstants.TEST_3_ACTIVITY.getTask().getIdentifier();

    private User user;

    private DynamoDBMapper mapper;

    private DynamoScheduledActivityDao activityDao;
    
    private DynamoScheduledActivity testSchActivity;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());

        user = new User();
        user.setHealthCode(HEALTH_CODE);
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());

        testSchActivity = new DynamoScheduledActivity();
        
        // This is the part that will need to be expanded per test.
        mapper = mock(DynamoDBMapper.class);
        when(mapper.load(any(DynamoScheduledActivity.class))).thenReturn(testSchActivity);
        activityDao = new DynamoScheduledActivityDao();
        activityDao.setDdbMapper(mapper);
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @SuppressWarnings("unchecked")
    private void mockMapperResults(final List<ScheduledActivity> activities) {
        // Mocks loading one of the supplied activities.
        when(mapper.load(any())).thenAnswer(invocation -> {
            DynamoScheduledActivity thisSchActivity = invocation.getArgumentAt(0, DynamoScheduledActivity.class);
            for (ScheduledActivity schActivity : activities) {
                if (thisSchActivity.getGuid().equals(schActivity.getGuid()) && thisSchActivity.getHealthCode().equals(schActivity.getHealthCode())) {
                    return thisSchActivity;
                }
            }
            return null;
        });
        // Mocks a query that returns all of the activities.
        final PaginatedQueryList<DynamoScheduledActivity> queryResults = (PaginatedQueryList<DynamoScheduledActivity>) mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(((List<DynamoScheduledActivity>)(List<?>)activities).iterator());
        when(queryResults.toArray()).thenReturn(activities.toArray());
        when(mapper.query((Class<DynamoScheduledActivity>) any(Class.class),
            (DynamoDBQueryExpression<DynamoScheduledActivity>) any(DynamoDBQueryExpression.class)))
            .thenReturn(queryResults);
        
        // Mock a batch load of the activities
        Map<String,List<Object>> results = Maps.newHashMap();
        results.put("some-table-name", new ArrayList<Object>(activities));
        when(mapper.batchLoad(any(List.class))).thenReturn(results);
    }
    
    @Test
    public void getScheduledActivities() throws Exception {
        ScheduledActivity activity = activityDao.getActivity(PACIFIC_TIME_ZONE, "AAA", "BBB");
        assertEquals(testSchActivity, activity);
        
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityThrowsException() throws Exception {
        when(mapper.load(any(DynamoScheduledActivity.class))).thenReturn(null);
        
        activityDao.getActivity(PACIFIC_TIME_ZONE, "AAA", "BBB");
    }

    /**
     * Testing retrieval of activities has gotten much simpler as we just load the activities we 
     * are asked to load. This test verifies that the happy path works of returning all the results, 
     * sorted.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetActivities() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        mockMapperResults(activities);
        List<ScheduledActivity> activities2 = activityDao.getActivities(context.getZone(), activities);

        // Activities are sorted first by date, then by label ("Activity1", "Activity2" & "Activity3")
        // Expired activities are not returned, so this starts on the 12th
        assertScheduledActivity(activities2.get(0), ACTIVITY_2_REF, "2015-04-12T13:00:00-07:00");
        assertScheduledActivity(activities2.get(1), ACTIVITY_2_REF, "2015-04-13T13:00:00-07:00");
        assertScheduledActivity(activities2.get(2), ACTIVITY_3_REF, "2015-04-13T13:00:00-07:00");
        assertScheduledActivity(activities2.get(3), ACTIVITY_1_REF, "2015-04-14T13:00:00-07:00");
        assertScheduledActivity(activities2.get(4), ACTIVITY_2_REF, "2015-04-14T13:00:00-07:00");

        verify(mapper).batchLoad((List<Object>)any());
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    public void testOnlyPersistedActivitiesReturned() {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        // Only mock the return of one of these activities
        mockMapperResults(Lists.newArrayList(activities.get(0)));
        
        List<ScheduledActivity> activities2 = activityDao.getActivities(context.getZone(), activities);

        // Regardless of the requested activities, only the ones in the db are returned (in this case, there's 1).
        assertEquals(1, activities2.size());
        assertScheduledActivity(activities2.get(0), ACTIVITY_2_REF, "2015-04-12T13:00:00-07:00");

        verify(mapper).batchLoad((List<Object>)any());
        verifyNoMoreInteractions(mapper);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void canDeleteActivities() {
        mockMapperResults(Lists.newArrayList(new DynamoScheduledActivity(), new DynamoScheduledActivity()));

        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DynamoDBQueryExpression> queryArg = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        activityDao.deleteActivitiesForUser("AAA");

        // This doesn't verify that the query was done with the "AAA" key
        verify(mapper).query((Class<DynamoScheduledActivity>) any(Class.class),
                        (DynamoDBQueryExpression<DynamoScheduledActivity>) queryArg.capture());
        
        verify(mapper).batchDelete(argument.capture());
        verifyNoMoreInteractions(mapper);

        DynamoDBQueryExpression query = queryArg.getValue();
        ScheduledActivity activity = (ScheduledActivity)query.getHashKeyValues();
        assertEquals("AAA", activity.getHealthCode());
        
        // Both activities were passed in to be deleted.
        assertEquals(2, argument.getValue().size());
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void canUpdateActivities() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setHealthCode(HEALTH_CODE);
        activity1.setActivity(TestConstants.TEST_3_ACTIVITY);
        activity1.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        activity1.setGuid(BridgeUtils.generateGuid());

        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setHealthCode(HEALTH_CODE);
        activity2.setActivity(TestConstants.TEST_3_ACTIVITY);
        activity2.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        activity2.setStartedOn(DateTime.parse("2015-04-13T18:05:23.000-07:00").getMillis());
        activity2.setFinishedOn(DateTime.parse("2015-04-13T18:20:23.000-07:00").getMillis());
        activity2.setGuid(BridgeUtils.generateGuid());
        
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2);
        activityDao.updateActivities(HEALTH_CODE, activities);

        // These activities have been updated.
        verify(mapper).batchSave(argument.capture());
        verifyNoMoreInteractions(mapper);
        
        assertEquals(activities, argument.getValue());
    }
    
    @Test
    public void callGetActivitiesWithEmptyListReturnsEmptyList() {
        List<ScheduledActivity> activities = activityDao.getActivities(DateTimeZone.UTC, new ArrayList<>());
        assertTrue(activities.isEmpty());
        assertTrue(activities instanceof ImmutableList);
        
        verifyNoMoreInteractions(mapper);
    }
    
    private void assertScheduledActivity(ScheduledActivity schActivity, String ref, String dateString) {
        DateTime date = DateTime.parse(dateString);
        assertTrue(date.isEqual(schActivity.getScheduledOn()));
        if (schActivity.getActivity().getActivityType() == ActivityType.TASK) {
            assertEquals(ref, schActivity.getActivity().getTask().getIdentifier());            
        } else {
            assertEquals(ref, schActivity.getActivity().getSurvey().getHref());
        }
    }

}
