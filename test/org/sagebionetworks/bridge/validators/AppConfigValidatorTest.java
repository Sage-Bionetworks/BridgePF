package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.joda.time.DateTime;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigValidatorTest {
    
    private static final SurveyReference UNRESOLVED_SURVEY_REF = new SurveyReference(null, "guid", null);
    private static final SurveyReference RESOLVED_SURVEY_REF = new SurveyReference(null, "guid", DateTime.now());
    private static final GuidCreatedOnVersionHolder RESOLVED_SURVEY_KEYS = new GuidCreatedOnVersionHolderImpl(RESOLVED_SURVEY_REF);
    private static final SchemaReference UNRESOLVED_SCHEMA_REF = new SchemaReference("guid",null);
    private static final SchemaReference RESOLVED_SCHEMA_REF = new SchemaReference("guid", 3);
    
    @Mock
    private SurveyService surveyService;
    
    @Mock
    private UploadSchemaService schemaService;
    
    private AppConfigValidator newValidator;
    
    private AppConfigValidator updateValidator;
    
    private AppConfig appConfig;
    
    @Before
    public void before() {
        appConfig = AppConfig.create();
        appConfig.setStudyId(TEST_STUDY_IDENTIFIER);
        
        this.newValidator = new AppConfigValidator(surveyService, schemaService, Sets.newHashSet("foo","bar"), true);
        this.updateValidator = new AppConfigValidator(surveyService, schemaService, Sets.newHashSet("foo","bar"), false);
    }
    
    @Test
    public void guidRequired() {
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void labelRequired() {
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void studyIdRequired() {
        appConfig.setStudyId(null);
        assertValidatorMessage(newValidator, appConfig, "studyId", "is required");
        
        appConfig.setStudyId("");
        assertValidatorMessage(newValidator, appConfig, "studyId", "is required");
    }
    
    @Test
    public void criteriaAreRequired() {
        assertValidatorMessage(newValidator, appConfig, "criteria", "are required");
    }
    
    @Test
    public void surveyReferencesHaveCreatedOnTimestamps() { 
        appConfig.getSurveyReferences().add(UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0].createdOn", "is required");
    }
    
    @Test
    public void schemaReferencesHaveRevision() { 
        appConfig.getSchemaReferences().add(UNRESOLVED_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].revision", "is required");
    }
    
    @Test
    public void schemaDoesNotExistOnCreate() {
        when(schemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "guid", 3))
                .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(RESOLVED_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    @Test
    public void schemaDoesNotExistOnUpdate() {
        when(schemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "guid", 3))
            .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(RESOLVED_SCHEMA_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    
    @Test
    public void surveyDoesNotExistOnCreate() {
        when(surveyService.getSurvey(RESOLVED_SURVEY_KEYS, false)).thenThrow(new EntityNotFoundException(Survey.class));
        
        appConfig.getSurveyReferences().add(RESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void surveyDoesNotExistOnUpdate() {
        when(surveyService.getSurvey(RESOLVED_SURVEY_KEYS, false)).thenThrow(new EntityNotFoundException(Survey.class));
        
        appConfig.getSurveyReferences().add(RESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }    
    
    @Test
    public void surveyIsNotPublishedOnCreate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(RESOLVED_SURVEY_KEYS, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(RESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void surveyIsNotPublishedOnUpdate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(RESOLVED_SURVEY_KEYS, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(RESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(newValidator, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: bar, foo");
    }
}
