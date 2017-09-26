package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityScheduler;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@SuppressWarnings("unchecked")
public class ScheduledActivityServiceResolveLinksTest {
    private static final String ACTIVITY_LABEL_PREFIX = "test-activity-";
    private static final String COMPOUND_ACTIVITY_REF_TASK_ID = "compound-activity-ref";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 42;
    private static final String SURVEY_GUID = "test-survey-guid";
    private static final String SURVEY_ID = "test-survey";
    private static final String TASK_ID = "non-compound-task";

    // We need survey createdOn in both long millis form and in Joda DateTime form.
    private static final long SURVEY_CREATED_ON_MILLIS = 1234;
    private static final DateTime SURVEY_CREATED_ON_DATE_TIME = new DateTime(SURVEY_CREATED_ON_MILLIS);

    // We only care about ClientInfo (which is automatically populated to ClientInfo.UNKNOWN_CLIENT) and study ID.
    private static final ScheduleContext SCHEDULE_CONTEXT = new ScheduleContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();

    private CompoundActivityDefinitionService mockCompoundActivityDefinitionService;
    private SchedulePlanService mockSchedulePlanService;
    //private UploadSchemaService mockSchemaService;
    //private SurveyService mockSurveyService;
    private ScheduledActivityService scheduledActivityService;
    private AppConfigService appConfigService;
    private AppConfig appConfig;
    private ModelReferenceResolver modelReferenceResolver;

    @Before
    public void setup() {
        // Mock compound activity definition service. This compound activity contains a schema ref with an unresolved
        // revision, and a survey reference with no createdOn (published survey).
        CompoundActivityDefinition compoundActivityDefinition = CompoundActivityDefinition.create();
        compoundActivityDefinition.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        compoundActivityDefinition.setTaskId(COMPOUND_ACTIVITY_REF_TASK_ID);
        compoundActivityDefinition.setSchemaList(ImmutableList.of(new SchemaReference(SCHEMA_ID, null)));
        compoundActivityDefinition.setSurveyList(ImmutableList.of(new SurveyReference(SURVEY_ID, SURVEY_GUID, null)));

        mockCompoundActivityDefinitionService = mock(CompoundActivityDefinitionService.class);
        when(mockCompoundActivityDefinitionService.getCompoundActivityDefinition(TestConstants.TEST_STUDY,
                COMPOUND_ACTIVITY_REF_TASK_ID)).thenReturn(compoundActivityDefinition);

        // Mock scheduler plan service. This is filled out in setupSchedulePlanServiceWithActivity().
        mockSchedulePlanService = mock(SchedulePlanService.class);

        // Mock schema service to provide a concrete schema. Schema only cares about ID and rev.
        UploadSchema schema = UploadSchema.create();
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaId(SCHEMA_ID);
        schema.setRevision(SCHEMA_REV);

        // Similarly, mock Survey.
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        survey.setIdentifier(SURVEY_ID);
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON_MILLIS);

        appConfig = AppConfig.create();
        appConfig.setSurveyReferences(Lists
                .newArrayList(new SurveyReference(SURVEY_ID, SURVEY_GUID, new DateTime(SURVEY_CREATED_ON_MILLIS))));
        appConfig.setSchemaReferences(Lists.newArrayList(new SchemaReference(SCHEMA_ID, SCHEMA_REV)));
        
        appConfigService = mock(AppConfigService.class);
        when(appConfigService.getAppConfigForUser(any())).thenReturn(appConfig);
        
        // Set up scheduled activity service with the mocks.
        scheduledActivityService = spy(new ScheduledActivityService());
        scheduledActivityService.setCompoundActivityDefinitionService(mockCompoundActivityDefinitionService);
        scheduledActivityService.setSchedulePlanService(mockSchedulePlanService);
        scheduledActivityService.setAppConfigService(appConfigService);
        
        // Spy modelReferenceResolver because we're going to verify that specific references are looked up
        modelReferenceResolver = spy(new ModelReferenceResolver(appConfig));
        when(scheduledActivityService.getModelReferenceResolver(appConfig)).thenReturn(modelReferenceResolver);
    }

    private void setupSchedulePlanServiceWithActivity(Activity activity) {
        // Create a schedule plan, but don't do anything with it yet. There are quite a few circular dependenices that
        // we need to fulfill to fully set this up.
        SchedulePlan plan = SchedulePlan.create();

        // Add a label to the activity. This serves 2 purposes: (1) it tests that we're copying activity attributes
        // correctly and (2) it allows us to distinguish this activity from other activities.
        Activity activityCopy1 = new Activity.Builder().withActivity(activity).withLabel(ACTIVITY_LABEL_PREFIX + "1")
                .build();

        // To test caching, the schedule plan service will return a list with two identical activities.
        Activity activityCopy2 = new Activity.Builder().withActivity(activity).withLabel(ACTIVITY_LABEL_PREFIX + "2")
                .build();

        // We need to wrap activities in scheduled activities.
        ScheduledActivity scheduledActivity = ScheduledActivity.create();
        scheduledActivity.setActivity(activityCopy1);

        ScheduledActivity scheduledActivityCopy = ScheduledActivity.create();
        scheduledActivityCopy.setActivity(activityCopy2);

        List<ScheduledActivity> scheduledActivityList = ImmutableList.of(scheduledActivity, scheduledActivityCopy);

        // Activity lists come from an ActivityScheduler, which is a complicated thing that we'll mock for the purposes
        // of this test.
        ActivityScheduler mockScheduler = mock(ActivityScheduler.class);
        when(mockScheduler.getScheduledActivities(plan, SCHEDULE_CONTEXT)).thenReturn(scheduledActivityList);

        // Similarly, mock schedule to return a scheduler.
        Schedule mockSchedule = mock(Schedule.class);
        when(mockSchedule.getScheduler()).thenReturn(mockScheduler);

        // We need a schedule strategy to return the schedule. SimpleScheduleStrategy fits the bill.
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(mockSchedule);

        // Now we can add the schedule strategy to the schedule plan.
        plan.setStrategy(strategy);

        // And the schedule plan service returns the schedule plan.
        when(mockSchedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TestConstants.TEST_STUDY))
                .thenReturn(ImmutableList.of(plan));
    }

    @Test
    public void resolveCompoundActivityReference() {
        // Create a compound activity reference (has an ID but no schema or survey lists).
        CompoundActivity inputCompoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(COMPOUND_ACTIVITY_REF_TASK_ID).build();
        Activity activity = new Activity.Builder().withCompoundActivity(inputCompoundActivity).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyCompoundActivities(scheduledActivityList);

        // Validate backends. We only called compound activity, schema, and survey services once.
        verify(mockCompoundActivityDefinitionService, times(1)).getCompoundActivityDefinition(any(), any());
        
        verify(modelReferenceResolver, times(1)).getSchemaReference(any());
        verify(modelReferenceResolver, times(1)).getSurveyReference(any());
    }

    @Test
    public void resolveCompoundActivityWithListOfReferences() {
        // Create a compound activity that has a list of unresolved schema and survey references.
        CompoundActivity inputCompoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(COMPOUND_ACTIVITY_REF_TASK_ID)
                .withSchemaList(ImmutableList.of(new SchemaReference(SCHEMA_ID, null)))
                .withSurveyList(ImmutableList.of(new SurveyReference(SURVEY_ID, SURVEY_GUID, null)))
                .build();
        Activity activity = new Activity.Builder().withCompoundActivity(inputCompoundActivity).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyCompoundActivities(scheduledActivityList);

        // Validate backends. We only called schema and survey services once. We never call compound activity service.
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, times(1)).getSchemaReference(any());
        verify(modelReferenceResolver, times(1)).getSurveyReference(any());
    }

    @Test
    public void resolveCompoundActivityAlreadyResolved() {
        // Create a compound activity that has a list of unresolved schema and survey references.
        CompoundActivity inputCompoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(COMPOUND_ACTIVITY_REF_TASK_ID)
                .withSchemaList(ImmutableList.of(new SchemaReference(SCHEMA_ID, SCHEMA_REV)))
                .withSurveyList(ImmutableList.of(new SurveyReference(SURVEY_ID, SURVEY_GUID,
                        SURVEY_CREATED_ON_DATE_TIME)))
                .build();
        Activity activity = new Activity.Builder().withCompoundActivity(inputCompoundActivity).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyCompoundActivities(scheduledActivityList);

        // Validate we never call any backends.
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    @Test
    public void resolveCompoundActivity404() {
        // Create a compound activity reference (has an ID but no schema or survey lists).
        CompoundActivity inputCompoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(COMPOUND_ACTIVITY_REF_TASK_ID).build();
        Activity activity = new Activity.Builder().withCompoundActivity(inputCompoundActivity).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Mock compound activity service to throw.
        when(mockCompoundActivityDefinitionService.getCompoundActivityDefinition(TestConstants.TEST_STUDY,
                COMPOUND_ACTIVITY_REF_TASK_ID)).thenThrow(EntityNotFoundException.class);

        // Execute and validate. We have 2 activities (because of how the test is set up), but the activities don't
        // have schema lists or survey lists.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            CompoundActivity compoundActivity = oneScheduledActivity.getActivity().getCompoundActivity();
            assertEquals(COMPOUND_ACTIVITY_REF_TASK_ID, compoundActivity.getTaskIdentifier());
            assertTrue(compoundActivity.getSchemaList().isEmpty());
            assertTrue(compoundActivity.getSurveyList().isEmpty());
        }

        // Validate backends.
        verify(mockCompoundActivityDefinitionService, times(2)).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    @Test
    public void resolveCompoundActivityWithListOf404s() {
        // Create a compound activity reference (has an ID but no schema or survey lists).
        // Create a compound activity that has a list of unresolved schema and survey references.
        CompoundActivity inputCompoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(COMPOUND_ACTIVITY_REF_TASK_ID)
                .withSchemaList(ImmutableList.of(new SchemaReference(SCHEMA_ID, null)))
                .withSurveyList(ImmutableList.of(new SurveyReference(SURVEY_ID, SURVEY_GUID, null)))
                .build();
        Activity activity = new Activity.Builder().withCompoundActivity(inputCompoundActivity).build();
        setupSchedulePlanServiceWithActivity(activity);

        appConfig.setSurveyReferences(Lists.newArrayList());
        appConfig.setSchemaReferences(Lists.newArrayList());
        modelReferenceResolver = spy(new ModelReferenceResolver(appConfig));
        when(scheduledActivityService.getModelReferenceResolver(appConfig)).thenReturn(modelReferenceResolver);

        // Execute and validate. We have 2 activities (because of how the test is set up), and the activities have
        // empty schema and survey lists because we remove the ones that throw 404.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            CompoundActivity compoundActivity = oneScheduledActivity.getActivity().getCompoundActivity();
            assertEquals(COMPOUND_ACTIVITY_REF_TASK_ID, compoundActivity.getTaskIdentifier());
            assertTrue(compoundActivity.getSchemaList().isEmpty());
            assertTrue(compoundActivity.getSurveyList().isEmpty());
        }

        // Validate backends - Note that even though resolveSchema() and resolveSurvey() don't cache exceptions,
        // resolveCompoundActivity does cache the resulting compound activity with unresolved references. So
        // schemaService and surveyService still only get called once.
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, times(1)).getSchemaReference(any());
        verify(modelReferenceResolver, times(1)).getSurveyReference(any());
    }

    private static void verifyCompoundActivities(List<ScheduledActivity> scheduledActivityList) {
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            CompoundActivity compoundActivity = oneScheduledActivity.getActivity().getCompoundActivity();
            assertEquals(COMPOUND_ACTIVITY_REF_TASK_ID, compoundActivity.getTaskIdentifier());

            assertEquals(1, compoundActivity.getSchemaList().size());
            assertEquals(SCHEMA_ID, compoundActivity.getSchemaList().get(0).getId());
            assertEquals(SCHEMA_REV, compoundActivity.getSchemaList().get(0).getRevision().intValue());

            assertEquals(1, compoundActivity.getSurveyList().size());
            assertEquals(SURVEY_ID, compoundActivity.getSurveyList().get(0).getIdentifier());
            assertEquals(SURVEY_GUID, compoundActivity.getSurveyList().get(0).getGuid());
            assertEquals(SURVEY_CREATED_ON_MILLIS, compoundActivity.getSurveyList().get(0).getCreatedOn().getMillis());
        }
    }

    @Test
    public void resolvePublishedSurvey() {
        // A published survey is a survey with no createdOn. It will be resolved to a createdOn at time of the
        // getScheduledActivities call.
        Activity activity = new Activity.Builder().withPublishedSurvey(SURVEY_ID, SURVEY_GUID).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifySurveys(scheduledActivityList);

        // We call survey service once.
        verify(modelReferenceResolver, times(1)).getSurveyReference(any());

        // Validate that we never call compound activity service or schema service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
        
    }

    @Test
    public void resolveSurveyAlreadyResolved() {
        // Create a survey activity that already has a createdOn. This will skip link resolution.
        Activity activity = new Activity.Builder().withSurvey(SURVEY_ID, SURVEY_GUID, SURVEY_CREATED_ON_DATE_TIME)
                .build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifySurveys(scheduledActivityList);

        // We never call survey service.
        verify(modelReferenceResolver, never()).getSurveyReference(any());

        // Validate that we never call compound activity service or schema service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
    }

    @Test
    public void resolveSurvey404() {
        // A published survey is a survey with no createdOn.
        Activity activity = new Activity.Builder().withPublishedSurvey(SURVEY_ID, SURVEY_GUID).build();
        setupSchedulePlanServiceWithActivity(activity);

        appConfig.setSurveyReferences(Lists.newArrayList());
        modelReferenceResolver = spy(new ModelReferenceResolver(appConfig));
        when(scheduledActivityService.getModelReferenceResolver(appConfig)).thenReturn(modelReferenceResolver);

        // Execute and validate. We have 2 activities (because of how the test is set up), but the activities don't
        // have surveyCreatedOn.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            SurveyReference surveyRef = oneScheduledActivity.getActivity().getSurvey();
            assertEquals(SURVEY_ID, surveyRef.getIdentifier());
            assertEquals(SURVEY_GUID, surveyRef.getGuid());
            assertNull(surveyRef.getCreatedOn());
        }

        // We call survey service twice (because we don't cache exception).
        verify(modelReferenceResolver, times(2)).getSurveyReference(any());

        // Validate that we never call compound activity service or schema service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
    }

    private static void verifySurveys(List<ScheduledActivity> scheduledActivityList) {
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            SurveyReference surveyRef = oneScheduledActivity.getActivity().getSurvey();
            assertEquals(SURVEY_ID, surveyRef.getIdentifier());
            assertEquals(SURVEY_GUID, surveyRef.getGuid());
            assertEquals(SURVEY_CREATED_ON_MILLIS, surveyRef.getCreatedOn().getMillis());
        }
    }

    @Test
    public void resolveSchemaWithoutRev() {
        // Create a schema ref without a rev. The rev will be resolved by schema service.
        SchemaReference schemaRef = new SchemaReference(SCHEMA_ID, null);
        TaskReference taskRef = new TaskReference(TASK_ID, schemaRef);
        Activity activity = new Activity.Builder().withTask(taskRef).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifySchemas(scheduledActivityList);

        // We call schema service once.
        verify(modelReferenceResolver, times(1)).getSchemaReference(any());

        // Validate that we never call compound activity service or survey service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    @Test
    public void resolveSchemaAlreadyResolved() {
        // Create a schema ref already with a rev. This skips link resolution.
        SchemaReference schemaRef = new SchemaReference(SCHEMA_ID, SCHEMA_REV);
        TaskReference taskRef = new TaskReference(TASK_ID, schemaRef);
        Activity activity = new Activity.Builder().withTask(taskRef).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifySchemas(scheduledActivityList);

        // We never call schema service.
        verify(modelReferenceResolver, never()).getSchemaReference(any());

        // Validate that we never call compound activity service or survey service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    @Test
    public void resolveSchema404() {
        // Create a schema ref without a rev.
        SchemaReference schemaRef = new SchemaReference(SCHEMA_ID, null);
        TaskReference taskRef = new TaskReference(TASK_ID, schemaRef);
        Activity activity = new Activity.Builder().withTask(taskRef).build();
        setupSchedulePlanServiceWithActivity(activity);

        appConfig.setSchemaReferences(Lists.newArrayList());
        modelReferenceResolver = spy(new ModelReferenceResolver(appConfig));
        when(scheduledActivityService.getModelReferenceResolver(appConfig)).thenReturn(modelReferenceResolver);

        // Execute and validate. We have 2 activities (because of how the test is set up), but the activities don't
        // have schemaRevision.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            SchemaReference resolvedSchemaRef = oneScheduledActivity.getActivity().getTask().getSchema();
            assertEquals(SCHEMA_ID, resolvedSchemaRef.getId());
            assertNull(resolvedSchemaRef.getRevision());
        }

        // We call schema service twice (because we don't cache exception).
        verify(modelReferenceResolver, times(2)).getSchemaReference(any());

        // Validate that we never call compound activity service or survey service (not like we have any reason to).
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    private static void verifySchemas(List<ScheduledActivity> scheduledActivityList) {
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            SchemaReference schemaRef = oneScheduledActivity.getActivity().getTask().getSchema();
            assertEquals(SCHEMA_ID, schemaRef.getId());
            assertEquals(SCHEMA_REV, schemaRef.getRevision().intValue());
        }
    }

    // branch coverage
    @Test
    public void resolveTaskRefWithoutSchema() {
        // Create a task without a schema. This will skip resolution on account of there's nothing to resolve.
        Activity activity = new Activity.Builder().withTask(TASK_ID).build();
        setupSchedulePlanServiceWithActivity(activity);

        // Execute.
        List<ScheduledActivity> scheduledActivityList = scheduledActivityService.scheduleActivitiesForPlans(
                SCHEDULE_CONTEXT);

        // Our setup creates 2 copies of the task, even though it doesn't matter for tasks w/o schemas.
        verifyActivityListSizeAndLabels(scheduledActivityList);
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            TaskReference taskRef = oneScheduledActivity.getActivity().getTask();
            assertEquals(TASK_ID, taskRef.getIdentifier());
            assertNull(taskRef.getSchema());
        }

        // No resolution happens, so we never call any of the backends.
        verify(mockCompoundActivityDefinitionService, never()).getCompoundActivityDefinition(any(), any());
        verify(modelReferenceResolver, never()).getSchemaReference(any());
        verify(modelReferenceResolver, never()).getSurveyReference(any());
    }

    private static void verifyActivityListSizeAndLabels(List<ScheduledActivity> scheduledActivityList) {
        assertEquals(2, scheduledActivityList.size());
        assertEquals(ACTIVITY_LABEL_PREFIX + "1", scheduledActivityList.get(0).getActivity().getLabel());
        assertEquals(ACTIVITY_LABEL_PREFIX + "2", scheduledActivityList.get(1).getActivity().getLabel());
    }
}
