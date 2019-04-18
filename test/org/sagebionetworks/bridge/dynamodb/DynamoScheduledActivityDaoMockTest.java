package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DynamoScheduledActivityDaoMockTest {

    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");

    private static final String HEALTH_CODE = "AAA";
    private static final DateTimeZone PACIFIC_TIME_ZONE = DateTimeZone.forOffsetHours(-7);
    
    private static final String BASE_URL = BridgeConfigFactory.getConfig().getWebservicesURL();
    private static final String ACTIVITY_1_REF = BASE_URL + "/v3/surveys/AAA/revisions/published";
    private static final String ACTIVITY_2_REF = BASE_URL + "/v3/surveys/BBB/revisions/published";
    private static final String ACTIVITY_3_REF = TestUtils.getActivity3().getTask().getIdentifier();
    private static final String ACTIVITY_GUID = "activityGuid";
    private static final DateTime SCHEDULED_ON_START = NOW.minusDays(1);
    private static final DateTime SCHEDULED_ON_END = NOW.plusDays(1);
    private static final String OFFSET_KEY = "offsetKey";
    private static final int PAGE_SIZE = 30;
    
    private DynamoDBMapper mapper;

    private DynamoScheduledActivityDao activityDao;
    
    private DynamoScheduledActivity testSchActivity;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());

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
            DynamoScheduledActivity thisSchActivity = invocation.getArgument(0);
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

        List<DynamoScheduledActivity> dynamoActivities = Lists.newArrayListWithCapacity(activities.size());
        for (ScheduledActivity activity : activities) {
            dynamoActivities.add((DynamoScheduledActivity)activity);
        }
        
        QueryResultPage<DynamoScheduledActivity> queryResultPage = (QueryResultPage<DynamoScheduledActivity>)mock(QueryResultPage.class);
        when(queryResultPage.getResults()).thenReturn(dynamoActivities);
        when(queryResultPage.getLastEvaluatedKey()).thenReturn(null);
        
        when(mapper.queryPage((Class<DynamoScheduledActivity>) any(Class.class),
            (DynamoDBQueryExpression<DynamoScheduledActivity>) any(DynamoDBQueryExpression.class)))
            .thenReturn(queryResultPage);
        
        // Mock a batch load of the activities
        Map<String,List<Object>> results = Maps.newHashMap();
        results.put("some-table-name", new ArrayList<Object>(activities));
        when(mapper.batchLoad(any(List.class))).thenReturn(results);
    }
    
    @Test
    public void getScheduledActivity() throws Exception {
        ScheduledActivity activity = activityDao.getActivity(PACIFIC_TIME_ZONE, "AAA", "BBB", true);
        assertEquals(testSchActivity, activity);
        assertEquals(PACIFIC_TIME_ZONE, activity.getTimeZone());
    }
    
    @Test
    public void getScheduledActivityWithNullReturnWorks() {
        reset(mapper);
        ScheduledActivity activity = activityDao.getActivity(PACIFIC_TIME_ZONE, "AAA", "BBB", false);
        assertNull(activity);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getActivityThrowsException() throws Exception {
        when(mapper.load(any(DynamoScheduledActivity.class))).thenReturn(null);
        
        activityDao.getActivity(PACIFIC_TIME_ZONE, "AAA", "BBB", true);
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
            .withInitialTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        mockMapperResults(activities);
        List<ScheduledActivity> activities2 = activityDao.getActivities(context.getInitialTimeZone(), activities);

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
    @Test
    public void testOnlyPersistedActivitiesReturned() {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withInitialTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        // Only mock the return of one of these activities
        mockMapperResults(Lists.newArrayList(activities.get(0)));
        
        List<ScheduledActivity> activities2 = activityDao.getActivities(context.getInitialTimeZone(), activities);

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
        
        activityDao.deleteActivitiesForUser("AAA");
        
        verify(mapper).queryPage(eq(DynamoScheduledActivity.class), any(DynamoDBQueryExpression.class));
        verify(mapper).batchDelete(argument.capture());
        
        assertEquals(2, argument.getValue().size());
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void canUpdateActivities() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setHealthCode(HEALTH_CODE);
        activity1.setActivity(TestUtils.getActivity3());
        activity1.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        activity1.setGuid(BridgeUtils.generateGuid());

        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setHealthCode(HEALTH_CODE);
        activity2.setActivity(TestUtils.getActivity3());
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

    @SuppressWarnings("unchecked")
    @Test
    public void getActivityHistoryV2() {
        DynamoIndexHelper indexHelper = mock(DynamoIndexHelper.class);
        QueryResultPage<DynamoScheduledActivity> queryResult = mock(QueryResultPage.class);
        activityDao.setReferentIndex(indexHelper);
        
        ArgumentCaptor<DynamoDBQueryExpression<DynamoScheduledActivity>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        List<DynamoScheduledActivity> list = ImmutableList.of(activity);
        when(queryResult.getResults()).thenReturn(list);

        when(mapper.queryPage(eq(DynamoScheduledActivity.class), any())).thenReturn(queryResult);

        Map<String, AttributeValue> map = ImmutableMap.of("guid", new AttributeValue(SCHEDULED_ON_START.toLocalDate()+":baz"));
        when(queryResult.getLastEvaluatedKey()).thenReturn(map);

        ForwardCursorPagedResourceList<ScheduledActivity> results = activityDao.getActivityHistoryV2(HEALTH_CODE,
                ACTIVITY_GUID, SCHEDULED_ON_START, SCHEDULED_ON_END, OFFSET_KEY, PAGE_SIZE);

        verify(mapper).queryPage(eq(DynamoScheduledActivity.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoScheduledActivity> query = queryCaptor.getValue();
        assertEquals(HEALTH_CODE, query.getHashKeyValues().getHealthCode());
        assertEquals(PAGE_SIZE, (int) query.getLimit());
        assertEquals(ACTIVITY_GUID+":"+OFFSET_KEY, query.getExclusiveStartKey().get("guid").getS() );
        assertEquals(HEALTH_CODE, query.getExclusiveStartKey().get("healthCode").getS() );
        Condition condition = query.getRangeKeyConditions().get("guid");

        assertEquals(ACTIVITY_GUID + ":" + SCHEDULED_ON_START.toLocalDateTime().toString(),
                condition.getAttributeValueList().get(0).getS());
        assertEquals(ACTIVITY_GUID + ":" + SCHEDULED_ON_END.toLocalDateTime().toString(),
                condition.getAttributeValueList().get(1).getS());

        assertEquals(1, results.getItems().size());
        assertEquals("baz", results.getNextPageOffsetKey());
        assertEquals(OFFSET_KEY, (String)results.getRequestParams().get("offsetKey"));
        assertEquals(PAGE_SIZE, (int)results.getRequestParams().get("pageSize"));
        assertEquals(SCHEDULED_ON_START.toString(), results.getRequestParams().get("scheduledOnStart"));
        assertEquals(SCHEDULED_ON_END.toString(), results.getRequestParams().get("scheduledOnEnd"));
    }

    @Test(expected = BadRequestException.class)
    public void getActivityHistoryV2PageBelowMinSize() {
        activityDao.getActivityHistoryV2(HEALTH_CODE, ACTIVITY_GUID, SCHEDULED_ON_START, SCHEDULED_ON_END, OFFSET_KEY,
                BridgeConstants.API_MINIMUM_PAGE_SIZE-2);
    }

    @Test(expected = BadRequestException.class)
    public void getActivityHistoryV2PageAboveMaxSize() {
        activityDao.getActivityHistoryV2(HEALTH_CODE, ACTIVITY_GUID, SCHEDULED_ON_START, SCHEDULED_ON_END, OFFSET_KEY,
                BridgeConstants.API_MAXIMUM_PAGE_SIZE+2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getActivityHistoryV3NoOffsetKey() {
        DynamoIndexHelper indexHelper = mock(DynamoIndexHelper.class);
        QueryResultPage<DynamoScheduledActivity> queryResultPage = mock(QueryResultPage.class);
        QueryOutcome outcome = mock(QueryOutcome.class);
        activityDao.setReferentIndex(indexHelper);
        
        Activity activity = TestUtils.getActivity1();
        String referentGuid = BridgeUtils.createReferentGuidIndex(activity, SCHEDULED_ON_START.toLocalDateTime());
        String guid = activity.getGuid() + ":" + SCHEDULED_ON_START.toLocalDateTime();

        // Need less than an entire page of records
        List<Item> items = new ArrayList<>();
        for (int i=0; i < 10; i++) {
            Item item = new Item().withString("guid", guid + ((i == 0) ? "" : i))
                    .with("referentGuid", referentGuid).with("healthCode", HEALTH_CODE);
            items.add(item);
        }
        when(outcome.getItems()).thenReturn(items);
        when(indexHelper.query(any())).thenReturn(outcome);

        List<Object> activities = new ArrayList<>();
        for (int i=0; i < 10; i++) {
            DynamoScheduledActivity oneActivity = new DynamoScheduledActivity(); 
            // These values are mostly to get us through sorting, and are not that important
            oneActivity.setGuid(guid + ((i == 0) ? "" : i));
            oneActivity.setReferentGuid(referentGuid);
            activities.add(oneActivity);
        }
        Map<String, List<Object>> resultMap = ImmutableMap.of("guid", activities);
        when(mapper.batchLoad(any(List.class))).thenReturn(resultMap);

        when(mapper.queryPage((Class<DynamoScheduledActivity>) any(Class.class),
            (DynamoDBQueryExpression<DynamoScheduledActivity>) any(DynamoDBQueryExpression.class)))
            .thenReturn(queryResultPage);
        ForwardCursorPagedResourceList<ScheduledActivity> results = activityDao.getActivityHistoryV3(HEALTH_CODE,
                ActivityType.TASK, activity.getGuid(), SCHEDULED_ON_START, SCHEDULED_ON_END,
                null, PAGE_SIZE);

        assertEquals(10, results.getItems().size());
        assertNull(results.getNextPageOffsetKey());
        assertNull(results.getRequestParams().get("offsetKey"));
        assertEquals(PAGE_SIZE, (int)results.getRequestParams().get("pageSize"));
        assertEquals(SCHEDULED_ON_START.toString(), results.getRequestParams().get("scheduledOnStart"));
        assertEquals(SCHEDULED_ON_END.toString(), results.getRequestParams().get("scheduledOnEnd"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getActivityHistoryV3() {
        DynamoIndexHelper indexHelper = mock(DynamoIndexHelper.class);
        QueryOutcome outcome = mock(QueryOutcome.class);
        activityDao.setReferentIndex(indexHelper);
        
        Activity activity = TestUtils.getActivity1();
        String referentGuid = BridgeUtils.createReferentGuidIndex(activity, SCHEDULED_ON_START.toLocalDateTime());
        String guid = activity.getGuid() + ":" + SCHEDULED_ON_START.toLocalDateTime();

        // Need an entire page of records
        List<Item> items = new ArrayList<>();
        for (int i=0; i < 31; i++) {
            Item item = new Item().withString("guid", guid + ((i == 0) ? "" : i))
                    .with("referentGuid", referentGuid).with("healthCode", HEALTH_CODE);
            items.add(item);
        }
        when(outcome.getItems()).thenReturn(items);
        when(indexHelper.query(any())).thenReturn(outcome);

        List<Object> activities = new ArrayList<>();
        for (int i=0; i < 31; i++) {
            DynamoScheduledActivity oneActivity = new DynamoScheduledActivity(); 
            // These values are mostly to get us through sorting, and are not that important
            oneActivity.setGuid(guid + ((i == 0) ? "" : i));
            oneActivity.setReferentGuid(referentGuid);
            activities.add(oneActivity);
        }
        Map<String, List<Object>> resultMap = ImmutableMap.of("guid", activities);
        when(mapper.batchLoad(any(List.class))).thenReturn(resultMap);

        ForwardCursorPagedResourceList<ScheduledActivity> results = activityDao.getActivityHistoryV3(HEALTH_CODE,
                ActivityType.TASK, activity.getGuid(), SCHEDULED_ON_START, SCHEDULED_ON_END,
                guid, PAGE_SIZE);

        assertEquals(30, results.getItems().size());
        assertEquals("activity1guid:2015-04-11T14:20:56.1239", results.getNextPageOffsetKey());
        assertEquals("activity1guid:2015-04-11T14:20:56.123", results.getRequestParams().get("offsetKey"));
        assertEquals(PAGE_SIZE, (int)results.getRequestParams().get("pageSize"));
        assertEquals(SCHEDULED_ON_START.toString(), results.getRequestParams().get("scheduledOnStart"));
        assertEquals(SCHEDULED_ON_END.toString(), results.getRequestParams().get("scheduledOnEnd"));
    }

    @Test(expected = BadRequestException.class)
    public void getActivityHistoryV3OffsetKeyIsNotInResults() {
        DynamoIndexHelper indexHelper = mock(DynamoIndexHelper.class);
        QueryOutcome outcome = mock(QueryOutcome.class);
        activityDao.setReferentIndex(indexHelper);
        
        Activity activity = TestUtils.getActivity1();
        String referentGuid = BridgeUtils.createReferentGuidIndex(activity, SCHEDULED_ON_START.toLocalDateTime());
        String guid = activity.getGuid() + ":" + SCHEDULED_ON_START.toLocalDateTime();

        // Need less than an entire page of records
        Item item = new Item().withString("guid", guid)
                .with("referentGuid", referentGuid).with("healthCode", HEALTH_CODE);
        List<Item> items = ImmutableList.of(item);
        when(outcome.getItems()).thenReturn(items);
        when(indexHelper.query(any())).thenReturn(outcome);

        activityDao.getActivityHistoryV3(HEALTH_CODE, ActivityType.TASK, activity.getGuid(), SCHEDULED_ON_START,
                SCHEDULED_ON_END, "03964c90-5944-4373-b5c8-db0dcc186e19:2018-11-18T11:03:08.902", PAGE_SIZE);
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void deleteActivities() {
        List<DynamoScheduledActivity> activities = Lists.newArrayList(new DynamoScheduledActivity(), new DynamoScheduledActivity());
        List<DynamoScheduledActivity> activities2 = Lists.newArrayList(new DynamoScheduledActivity());
        
        Map<String,AttributeValue> lastEvaluatedKey = ImmutableMap.of();
        
        QueryResultPage<DynamoScheduledActivity> queryResultPage = (QueryResultPage<DynamoScheduledActivity>) mock(
                QueryResultPage.class);
        when(queryResultPage.getResults()).thenReturn(activities, activities2);
        when(queryResultPage.getLastEvaluatedKey()).thenReturn(lastEvaluatedKey, (Map<String, AttributeValue>) null);
        
        when(mapper.queryPage((Class<DynamoScheduledActivity>) any(Class.class),
                (DynamoDBQueryExpression<DynamoScheduledActivity>) any(DynamoDBQueryExpression.class)))
                        .thenReturn(queryResultPage);
        
        // Mock a batch load of the activities
        Map<String,List<DynamoScheduledActivity>> results = Maps.newHashMap();
        results.put("some-table-name", activities);
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        
        activityDao.deleteActivitiesForUser("AAA");
        
        // Presence of a last evaluated key will cause delete to loop once.
        verify(mapper, times(2)).queryPage(eq(DynamoScheduledActivity.class), any(DynamoDBQueryExpression.class));
        verify(mapper, times(2)).batchDelete(argument.capture());
        assertEquals(2, argument.getAllValues().get(0).size());
        assertEquals(1, argument.getAllValues().get(1).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deleteActivitiesEmptyListDoesNotCallBatchDelete() {
        QueryResultPage<DynamoScheduledActivity> queryResultPage = (QueryResultPage<DynamoScheduledActivity>)mock(QueryResultPage.class);
        when(queryResultPage.getResults()).thenReturn(ImmutableList.of());
        when(queryResultPage.getLastEvaluatedKey()).thenReturn(null);
        
        when(mapper.queryPage((Class<DynamoScheduledActivity>) any(Class.class),
            (DynamoDBQueryExpression<DynamoScheduledActivity>) any(DynamoDBQueryExpression.class)))
            .thenReturn(queryResultPage);
        
        activityDao.deleteActivitiesForUser("AAA");
        verify(mapper, never()).batchDelete(any(List.class));
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void updateActivities() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setHealthCode(HEALTH_CODE);
        activity1.setActivity(TestUtils.getActivity3());
        activity1.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        activity1.setGuid(BridgeUtils.generateGuid());

        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setHealthCode(HEALTH_CODE);
        activity2.setActivity(TestUtils.getActivity3());
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
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateActivitiesWithEmptyListDoesNotCallBatchSave() {
        activityDao.updateActivities(HEALTH_CODE, ImmutableList.of());
        verify(mapper, never()).batchSave(any(List.class));
    }
    
    @Test
    public void getActivitiesWithEmptyListReturnsEmptyList() {
        List<ScheduledActivity> activities = activityDao.getActivities(DateTimeZone.UTC, new ArrayList<>());
        assertTrue(activities.isEmpty());
        assertTrue(activities instanceof ImmutableList);
        
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void saveActivities() {
        List<ScheduledActivity> activities = ImmutableList.of(ScheduledActivity.create(), ScheduledActivity.create());
        activityDao.saveActivities(activities);
        verify(mapper).batchSave(activities);
    }
    
    @Test
    public void saveActivitiesWithEmptyListDoesNotCallBatchSave() {
        List<ScheduledActivity> activities = ImmutableList.of();
        activityDao.saveActivities(activities);
        verify(mapper, never()).batchSave(activities);
    }  
    
    private Item makeActivity(String guid) {
        return new Item().withString("guid", guid)
                .with("referentGuid", "referent"+guid).with("healthCode", HEALTH_CODE);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void truncateToMiddleOfResultList() {
        List<Item> dynamoActivities = Lists.newArrayList(
            makeActivity("AAA:foo"),
            makeActivity("BBB:foo"),
            makeActivity("CCC:foo"), // <-- offset starts here
            makeActivity("DDD:foo"),
            makeActivity("EEE:foo"),
            makeActivity("FFF:foo"),
            makeActivity("GGG:foo") // <-- and must end before 10 records
        );
        
        DynamoIndexHelper indexHelper = mock(DynamoIndexHelper.class);
        activityDao.setReferentIndex(indexHelper);
        
        QueryOutcome outcome = mock(QueryOutcome.class);
        when(outcome.getItems()).thenReturn(dynamoActivities);
        
        when(indexHelper.query(any())).thenReturn(outcome);
        
        // This does not throw an out of bounds index exception, it correctly truncates the 
        // list at the start, and then calculates using that new sublist to determine it must 
        // return a list shorter than the required number of records.
        activityDao.getActivityHistoryV3(HEALTH_CODE, ActivityType.TASK, "guid", SCHEDULED_ON_START, SCHEDULED_ON_END,
                "CCC:foo", 10);
        
        ArgumentCaptor<Iterable> iterableCaptor = ArgumentCaptor.forClass(Iterable.class);
        
        verify(mapper).batchLoad(iterableCaptor.capture());
        
        List<DynamoScheduledActivity> capturedActivities = (List<DynamoScheduledActivity>)iterableCaptor.getValue();
        
        assertEquals(5, capturedActivities.size());
        assertEquals("CCC:foo", capturedActivities.get(0).getGuid());
        assertEquals("GGG:foo", capturedActivities.get(4).getGuid());
    }
}