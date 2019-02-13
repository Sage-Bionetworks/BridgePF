package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ReferenceResolverTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.UNKNOWN_CLIENT;
    private static final StudyIdentifier STUDY_ID = TestConstants.TEST_STUDY;
    private static final String SURVEY_ID = "surveyId";
    private static final String SURVEY_GUID = "guid";
    private static final DateTime SURVEY_CREATED_ON = DateTime.now();
    private static final String SCHEMA_ID = "schemaId";
    private static final int SCHEMA_REVISION = 3;
    private static final String TASK_ID = "taskId";
    private static final SurveyReference RESOLVED_SURVEY_REF = new SurveyReference(SURVEY_ID, SURVEY_GUID, SURVEY_CREATED_ON);
    private static final SchemaReference RESOLVED_SCHEMA_REF = new SchemaReference(SCHEMA_ID, SCHEMA_REVISION);
    private static final TaskReference RESOLVED_TASK_REF = new TaskReference(TASK_ID, RESOLVED_SCHEMA_REF);
    private static final SurveyReference UNRESOLVED_SURVEY_REF = new SurveyReference(SURVEY_ID, SURVEY_GUID, null);
    private static final SurveyReference UNRESOLVED_SURVEY_ID_REF = new SurveyReference(null, SURVEY_GUID, SURVEY_CREATED_ON);
    private static final SchemaReference UNRESOLVED_SCHEMA_REF = new SchemaReference(SCHEMA_ID, null);
    private static final TaskReference UNRESOLVED_TASK_REF = new TaskReference(TASK_ID, UNRESOLVED_SCHEMA_REF);
    private static final CompoundActivity COMPOUND_ACTIVITY_SKINNY_REF = new CompoundActivity.Builder()
            .withTaskIdentifier(TASK_ID).build();
    private static final CompoundActivity RESOLVED_COMPOUND_ACTIVITY = new CompoundActivity.Builder()
        .withSurveyList(Lists.newArrayList(RESOLVED_SURVEY_REF))
        .withSchemaList(Lists.newArrayList(RESOLVED_SCHEMA_REF)).build();
    private static final CompoundActivity UNRESOLVED_COMPOUND_ACTIVITY = new CompoundActivity.Builder()
            .withSurveyList(Lists.newArrayList(UNRESOLVED_SURVEY_REF))
            .withSchemaList(Lists.newArrayList(UNRESOLVED_SCHEMA_REF)).build();
    private static final Survey SURVEY = Survey.create();
    static {
        SURVEY.setGuid(SURVEY_GUID);
        SURVEY.setCreatedOn(SURVEY_CREATED_ON.getMillis());
        SURVEY.setIdentifier(SURVEY_ID);
    }
    private static final UploadSchema SCHEMA = UploadSchema.create();
    static {
        SCHEMA.setSchemaId(SCHEMA_ID);
        SCHEMA.setRevision(SCHEMA_REVISION);
    }
    private static final CompoundActivityDefinition RESOLVED_COMPOUND_ACTIVITY_DEF = CompoundActivityDefinition.create();
    static {
        RESOLVED_COMPOUND_ACTIVITY_DEF.setSurveyList(Lists.newArrayList(RESOLVED_SURVEY_REF));
        RESOLVED_COMPOUND_ACTIVITY_DEF.setSchemaList(Lists.newArrayList(RESOLVED_SCHEMA_REF));
    }
    private static final CompoundActivityDefinition UNRESOLVED_COMPOUND_ACTIVITY_DEF = CompoundActivityDefinition.create();
    static {
        UNRESOLVED_COMPOUND_ACTIVITY_DEF.setSurveyList(Lists.newArrayList(UNRESOLVED_SURVEY_REF));
        UNRESOLVED_COMPOUND_ACTIVITY_DEF.setSchemaList(Lists.newArrayList(UNRESOLVED_SCHEMA_REF));
    }

    @Mock
    CompoundActivityDefinitionService compoundActivityDefinitionService;
    
    @Mock
    UploadSchemaService schemaService;
    
    @Mock
    SurveyService surveyService;
    
    @Spy
    private HashMap<String,SurveyReference> surveyReferences;
    
    @Spy
    private HashMap<String,SchemaReference> schemaReferences;
    
    private ReferenceResolver resolver;
    
    private ScheduledActivity scheduledActivity;
    
    private Activity.Builder activityBuilder;
    
    @Before
    public void before() {
        // All the dependencies are mocks or mutable maps, and can be adjusted per test
        resolver = new ReferenceResolver(compoundActivityDefinitionService, schemaService, surveyService,
                surveyReferences, schemaReferences, CLIENT_INFO, STUDY_ID);
        
        scheduledActivity = ScheduledActivity.create();
        
        activityBuilder = new Activity.Builder();
    }
    
    @Test
    public void surveyAlreadyResolvedWithoutCalls() {
        scheduledActivity.setActivity(activityBuilder.withSurvey(RESOLVED_SURVEY_REF).build());
        
        resolver.resolve(scheduledActivity);
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verifyNoMoreInteractions(surveyReferences);
        verifyNoMoreInteractions(schemaReferences);
    }
    
    @Test
    public void schemaAlreadyResolvedWithoutCalls() {
        scheduledActivity.setActivity(activityBuilder.withTask(RESOLVED_TASK_REF).build());
        
        resolver.resolve(scheduledActivity);
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verifyNoMoreInteractions(surveyReferences);
        verifyNoMoreInteractions(schemaReferences);
    }

    @Test
    public void compoundActivityAlreadyResolvedWithoutCalls() {
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(RESOLVED_COMPOUND_ACTIVITY).build());
        
        resolver.resolve(scheduledActivity);
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verifyNoMoreInteractions(surveyReferences);
        verifyNoMoreInteractions(schemaReferences);
    }

    @Test
    public void surveyResolvedFromAppConfig() {
        surveyReferences.put(SURVEY_GUID, RESOLVED_SURVEY_REF);
        scheduledActivity.setActivity(activityBuilder.withSurvey(UNRESOLVED_SURVEY_REF).build());
        
        resolver.resolve(scheduledActivity);
        
        assertEquals(RESOLVED_SURVEY_REF, scheduledActivity.getActivity().getSurvey());
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verify(surveyReferences).get(SURVEY_GUID);
        verifyNoMoreInteractions(schemaReferences);
    }
    
    @Test
    public void surveyResolvedFromServiceAndCached() {
        scheduledActivity.setActivity(activityBuilder.withSurvey(UNRESOLVED_SURVEY_REF).build());
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenReturn(SURVEY);
        
        resolver.resolve(scheduledActivity);
        
        assertEquals(RESOLVED_SURVEY_REF, scheduledActivity.getActivity().getSurvey());
        
        resolver.resolve(scheduledActivity);
        
        verify(surveyService, times(1)).getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false);
    }
    
    @Test
    public void surveyWithoutIdentifierResolvedFromServiceAndCached() {
        scheduledActivity.setActivity(activityBuilder.withSurvey(UNRESOLVED_SURVEY_ID_REF).build());
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenReturn(SURVEY);
        
        resolver.resolve(scheduledActivity);
        
        assertEquals(RESOLVED_SURVEY_REF, scheduledActivity.getActivity().getSurvey());
        
        resolver.resolve(scheduledActivity);
        
        verify(surveyService, times(1)).getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false);
    }
    
    @Test
    public void schemaResolvedFromAppConfig() {
        schemaReferences.put(SCHEMA_ID, RESOLVED_SCHEMA_REF);
        scheduledActivity.setActivity(activityBuilder.withTask(UNRESOLVED_TASK_REF).build());
        
        resolver.resolve(scheduledActivity);
        
        assertEquals(RESOLVED_SCHEMA_REF, scheduledActivity.getActivity().getTask().getSchema());
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verifyNoMoreInteractions(surveyReferences);
        verify(schemaReferences).get(SCHEMA_ID);
    }
    
    @Test
    public void schemaResolvedFromServiceAndCached() {
        scheduledActivity.setActivity(activityBuilder.withTask(UNRESOLVED_TASK_REF).build());
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO)).thenReturn(SCHEMA);
        
        resolver.resolve(scheduledActivity);
        
        assertEquals(RESOLVED_SCHEMA_REF, scheduledActivity.getActivity().getTask().getSchema());
        
        resolver.resolve(scheduledActivity);
        
        verify(schemaService, times(1)).getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO);
    }
    
    @Test
    public void compoundActivityReferenceFullyResolvedAndCached() {
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(COMPOUND_ACTIVITY_SKINNY_REF).build());
        when(compoundActivityDefinitionService.getCompoundActivityDefinition(STUDY_ID, TASK_ID))
                .thenReturn(RESOLVED_COMPOUND_ACTIVITY_DEF);

        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        resolver.resolve(scheduledActivity);
        
        verify(compoundActivityDefinitionService, times(1)).getCompoundActivityDefinition(STUDY_ID, TASK_ID);
    }

    @Test
    public void compoundActivitySkinnyReferenceResolvedFromSkinnyDefinitionAndCached() {
        // Verify here that a skinny ref retrieves a compound activity definition, and then resolves all the 
        // unresolved references in that definition.
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(COMPOUND_ACTIVITY_SKINNY_REF).build());
        when(compoundActivityDefinitionService.getCompoundActivityDefinition(STUDY_ID, TASK_ID))
                .thenReturn(UNRESOLVED_COMPOUND_ACTIVITY_DEF);
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenReturn(SURVEY);
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO)).thenReturn(SCHEMA);

        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        resolver.resolve(scheduledActivity);
        
        verify(compoundActivityDefinitionService, times(1)).getCompoundActivityDefinition(STUDY_ID, TASK_ID);
    }
    
    @Test
    public void compoundActivityFullyResolvedFromAppConfig() {
        schemaReferences.put(SCHEMA_ID, RESOLVED_SCHEMA_REF);
        surveyReferences.put(SURVEY_GUID, RESOLVED_SURVEY_REF);
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(UNRESOLVED_COMPOUND_ACTIVITY).build());
        
        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        verifyNoMoreInteractions(surveyService);
        verifyNoMoreInteractions(schemaService);
        verify(surveyReferences).get(SURVEY_GUID);
        verify(schemaReferences).get(SCHEMA_ID);
    }
    
    @Test
    public void compoundActivityOnlySchemaResolvedFromAppConfig() {
        // In this test, there is no resolved survey reference in the app config. The service will be called
        // to fully resolve. 
        schemaReferences.put(SCHEMA_ID, RESOLVED_SCHEMA_REF);
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(UNRESOLVED_COMPOUND_ACTIVITY).build());
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenReturn(SURVEY);
        
        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        verify(surveyService).getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false);
        verifyNoMoreInteractions(schemaService);
        verify(surveyReferences).get(SURVEY_GUID);
        verify(schemaReferences).get(SCHEMA_ID);
    }
    
    @Test
    public void compoundActivityOnlySurveyResolvedFromAppConfig() {
        // In this test, there is no resolved schema reference in the app config. The service will be called
        // to fully resolve. 
        surveyReferences.put(SURVEY_GUID, RESOLVED_SURVEY_REF);
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(UNRESOLVED_COMPOUND_ACTIVITY).build());
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO)).thenReturn(SCHEMA);
        
        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        verifyNoMoreInteractions(surveyService);
        verify(schemaService).getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO);
        verify(surveyReferences).get(SURVEY_GUID);
        verify(schemaReferences).get(SCHEMA_ID);
    }
    
    @Test
    public void compoundActivityResolvedFromServiceAndCached() {
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(UNRESOLVED_COMPOUND_ACTIVITY).build());
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenReturn(SURVEY);
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO)).thenReturn(SCHEMA);
        
        resolver.resolve(scheduledActivity);
        
        CompoundActivity compoundActivity = scheduledActivity.getActivity().getCompoundActivity();
        assertEquals(RESOLVED_SCHEMA_REF, compoundActivity.getSchemaList().get(0));
        assertEquals(RESOLVED_SURVEY_REF, compoundActivity.getSurveyList().get(0));
        
        resolver.resolve(scheduledActivity);
        
        verify(compoundActivityDefinitionService, never()).getCompoundActivityDefinition(STUDY_ID, TASK_ID);
        verify(surveyService, times(1)).getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false);
        verify(schemaService, times(1)).getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO);
    }
    
    @Test
    public void unresolvableSurveyReturnedAsIs() {
        scheduledActivity.setActivity(activityBuilder.withSurvey(UNRESOLVED_SURVEY_REF).build());
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false)).thenThrow(new EntityNotFoundException(Survey.class));
        
        resolver.resolve(scheduledActivity);
        
        assertNull(scheduledActivity.getActivity().getSurvey().getCreatedOn());
    }
    
    @Test
    public void unresolvableSchemaReturnedAsIs() {
        scheduledActivity.setActivity(activityBuilder.withTask(UNRESOLVED_TASK_REF).build());
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO)).thenThrow(new EntityNotFoundException(UploadSchema.class));
        
        resolver.resolve(scheduledActivity);
        
        assertNull(scheduledActivity.getActivity().getTask().getSchema().getRevision());
    }
    
    @Test
    public void taskWithoutSchemaReferenceReturnedAsIs() {
        TaskReference taskRef = new TaskReference(TASK_ID, null);
        scheduledActivity.setActivity(activityBuilder.withTask(taskRef).build());
        
        resolver.resolve(scheduledActivity);
        
        assertNull(scheduledActivity.getActivity().getTask().getSchema());
    }
    
    @Test
    public void unresolvableCompoundActivityReturnedAsIs() {
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(COMPOUND_ACTIVITY_SKINNY_REF).build());
        when(compoundActivityDefinitionService.getCompoundActivityDefinition(STUDY_ID, TASK_ID)).thenThrow(new EntityNotFoundException(CompoundActivityDefinition.class));
        
        resolver.resolve(scheduledActivity);
        
        assertTrue(scheduledActivity.getActivity().getCompoundActivity().getSchemaList().isEmpty());
        assertTrue(scheduledActivity.getActivity().getCompoundActivity().getSurveyList().isEmpty());
    }

    @Test
    public void compoundActivityWithUnresolvableReferencesReturnedAsIs() {
        scheduledActivity.setActivity(activityBuilder.withCompoundActivity(COMPOUND_ACTIVITY_SKINNY_REF).build());
        when(compoundActivityDefinitionService.getCompoundActivityDefinition(STUDY_ID, TASK_ID)).thenReturn(UNRESOLVED_COMPOUND_ACTIVITY_DEF);
        when(surveyService.getSurveyMostRecentlyPublishedVersion(STUDY_ID, SURVEY_GUID, false))
                .thenThrow(new EntityNotFoundException(Survey.class));
        when(schemaService.getLatestUploadSchemaRevisionForAppVersion(STUDY_ID, SCHEMA_ID, CLIENT_INFO))
                .thenThrow(new EntityNotFoundException(CompoundActivityDefinition.class));
        
        resolver.resolve(scheduledActivity);
        
        assertTrue(scheduledActivity.getActivity().getCompoundActivity().getSchemaList().isEmpty());
        assertTrue(scheduledActivity.getActivity().getCompoundActivity().getSurveyList().isEmpty());
    }
}
