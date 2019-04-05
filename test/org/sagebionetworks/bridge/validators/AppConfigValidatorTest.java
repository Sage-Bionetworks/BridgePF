package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;

import java.util.List;

import org.joda.time.DateTime;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.AppConfigElementService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigValidatorTest {
    
    private static final SurveyReference INVALID_SURVEY_REF = new SurveyReference(null, "guid", null);
    private static final SurveyReference VALID_UNRESOLVED_SURVEY_REF = new SurveyReference(null, "guid",
            DateTime.now());
    private static final GuidCreatedOnVersionHolder VALID_RESOLVED_SURVEY_KEYS = new GuidCreatedOnVersionHolderImpl(
            VALID_UNRESOLVED_SURVEY_REF);
    private static final SchemaReference INVALID_SCHEMA_REF = new SchemaReference("guid", null);
    private static final SchemaReference VALID_SCHEMA_REF = new SchemaReference("guid", 3);
    private static final ConfigReference VALID_CONFIG_REF = new ConfigReference("id", 3L);
    
    @Mock
    private SurveyService surveyService;
    
    @Mock
    private UploadSchemaService schemaService;
    
    @Mock
    private AppConfigElementService appConfigElementService;
    
    private AppConfigValidator newValidator;
    
    private AppConfigValidator updateValidator;
    
    private AppConfig appConfig;
    
    @Before
    public void before() {
        appConfig = AppConfig.create();
        appConfig.setStudyId(TEST_STUDY_IDENTIFIER);
        
        this.newValidator = new AppConfigValidator(surveyService, schemaService, appConfigElementService,
                TestConstants.USER_DATA_GROUPS, TestConstants.USER_SUBSTUDY_IDS, true);
        this.updateValidator = new AppConfigValidator(surveyService, schemaService, appConfigElementService,
                TestConstants.USER_DATA_GROUPS, TestConstants.USER_SUBSTUDY_IDS, false);
    }
    
    @Test
    public void configReferenceValidated() {
        ConfigReference ref1 = new ConfigReference("id:1", 1L);
        ConfigReference ref2 = new ConfigReference("id:2", 2L);
        
        when(appConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(AppConfigElement.create());
        
        List<ConfigReference> references = ImmutableList.of(ref1, ref2);
        appConfig.setConfigReferences(references);
        appConfig.setLabel("label");
        appConfig.setCriteria(Criteria.create());
        
        // This succeeds because the mock does not throw an exception
        Validate.entityThrowingException(newValidator, appConfig);
    }
    
    @Test
    public void configReferenceInvalid() {
        ConfigReference ref = new ConfigReference(null, null);
        
        appConfig.setConfigReferences(ImmutableList.of(ref));
        
        assertValidatorMessage(newValidator, appConfig, "configReferences[0].id", "is required");
        assertValidatorMessage(newValidator, appConfig, "configReferences[0].revision", "is required");
    }
    
    @Test
    public void configReferenceNotFound() { 
        ConfigReference ref1 = new ConfigReference("id:1", 1L);
        appConfig.setConfigReferences(ImmutableList.of(ref1));
        appConfig.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(appConfigElementService.getElementRevision(TestConstants.TEST_STUDY, "id:1", 1L))
                .thenThrow(new EntityNotFoundException(AppConfigElement.class));
        
        // This succeeds because the mock does not throw an exception
        assertValidatorMessage(newValidator, appConfig, "configReferences[0]", "does not refer to a configuration element");
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
        appConfig.getSurveyReferences().add(INVALID_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0].createdOn", "is required");
    }

    @Test
    public void schemaReferencesHaveId() {
        SchemaReference invalidSchemaRef = new SchemaReference(null, 3);
        appConfig.getSchemaReferences().add(invalidSchemaRef);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].id", "is required");
    }
    
    @Test
    public void schemaReferencesHaveRevision() { 
        appConfig.getSchemaReferences().add(INVALID_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].revision", "is required");
    }
    
    @Test
    public void schemaDoesNotExistOnCreate() {
        when(schemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "guid", 3))
                .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    @Test
    public void schemaDoesNotExistOnUpdate() {
        when(schemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "guid", 3))
            .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    
    @Test
    public void surveyDoesNotExistOnCreate() {
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void surveyDoesNotExistOnUpdate() {
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }    
    
    @Test
    public void surveyIsNotPublishedOnCreate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(TestConstants.TEST_STUDY, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void surveyIsNotPublishedOnUpdate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(surveyService.getSurvey(TestConstants.TEST_STUDY, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        criteria.setAllOfSubstudyIds(Sets.newHashSet("wrong-group"));
        
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(newValidator, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: group1, group2");
        assertValidatorMessage(newValidator, appConfig, "allOfSubstudyIds", "'wrong-group' is not in enumeration: substudyA, substudyB");
    }
    
    @Test
    public void rejectsReferenceToLogicallyDeletedSurvey() {
        Survey survey = Survey.create();
        survey.setDeleted(true);
        when(surveyService.getSurvey(TestConstants.TEST_STUDY, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void rejectsReferenceToLogicallyDeletedSchema() {
        UploadSchema schema = UploadSchema.create();
        schema.setDeleted(true);
        
        when(schemaService.getUploadSchemaByIdAndRev(TEST_STUDY, "guid", 3)).thenReturn(schema);
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }

    @Test
    public void rejectsReferenceToLogicallyDeletedConfigElement() {
        AppConfigElement element = AppConfigElement.create();
        element.setDeleted(true);
        
        when(appConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(element);
        
        appConfig.getConfigReferences().add(VALID_CONFIG_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "configReferences[0]", "does not refer to a configuration element");
    }
}
