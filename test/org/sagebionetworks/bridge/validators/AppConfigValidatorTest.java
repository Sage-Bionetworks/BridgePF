package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.joda.time.DateTime;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigValidatorTest {

    @Mock
    private SurveyService surveyService;
    
    @Mock
    private UploadSchemaService schemaService;
    
    private AppConfigValidator newValidator;
    
    private AppConfigValidator updateValidator;
    
    @Before
    public void before() {
        this.newValidator = new AppConfigValidator(surveyService, schemaService, Sets.newHashSet("foo","bar"), true);
        this.updateValidator = new AppConfigValidator(surveyService, schemaService, Sets.newHashSet("foo","bar"), false);
    }
    
    @Test
    public void guidRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void labelRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void studyIdRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(newValidator, appConfig, "studyId", "is required");
        
        appConfig.setStudyId("");
        assertValidatorMessage(newValidator, appConfig, "studyId", "is required");
    }
    
    @Test
    public void criteriaAreRequired() {
        AppConfig appConfig = AppConfig.create();
        
        assertValidatorMessage(newValidator, appConfig, "criteria", "are required");
    }
    
    @Test
    public void surveyReferencesHaveCreatedOnTimestamps() { 
        AppConfig appConfig = AppConfig.create();
        SurveyReference surveyRef = new SurveyReference("identifier","guid",null);
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0].createdOn", "is required");
    }
    
    @Test
    public void schemaReferencesHaveRevision() { 
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        SchemaReference schemaRef = new SchemaReference("guid",null);
        appConfig.getSchemaReferences().add(schemaRef);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].revision", "is required");
    }
    
    @Test
    public void schemaDoesNotExistOnCreate() {
        when(schemaService.getUploadSchema(any(), eq("guid"))).thenThrow(new EntityNotFoundException(AppConfig.class));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SchemaReference schemaRef = new SchemaReference("guid", 3);
        appConfig.getSchemaReferences().add(schemaRef);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    @Test
    public void schemaDoesNotExistOnUpdate() {
        when(schemaService.getUploadSchema(any(), eq("guid"))).thenThrow(new EntityNotFoundException(AppConfig.class));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SchemaReference schemaRef = new SchemaReference("guid", 3);
        appConfig.getSchemaReferences().add(schemaRef);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    
    @Test
    public void surveyDoesNotExistOnCreate() {
        when(surveyService.getSurvey(any(), eq(false))).thenThrow(new EntityNotFoundException(Survey.class));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SurveyReference surveyRef = new SurveyReference(null, "guid", DateTime.now());
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void surveyDoesNotExistOnUpdate() {
        when(surveyService.getSurvey(any(), eq(false))).thenThrow(new EntityNotFoundException(Survey.class));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SurveyReference surveyRef = new SurveyReference(null, "guid", DateTime.now());
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }    
    
    @Test
    public void surveyIsNotPublishedOnCreate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(any(), eq(false))).thenReturn(survey);
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SurveyReference surveyRef = new SurveyReference(null, "guid", DateTime.now());
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void surveyIsNotPublishedOnUpdate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(any(), eq(false))).thenReturn(survey);
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("studyId");
        SurveyReference surveyRef = new SurveyReference(null, "guid", DateTime.now());
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("test-study");
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(newValidator, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: bar, foo");
    }
}
