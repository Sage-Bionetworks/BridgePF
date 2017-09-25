package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

import com.google.common.collect.Sets;

public class AppConfigValidatorTest {

    private static final AppConfigValidator NEW_VALIDATOR = new AppConfigValidator(Sets.newHashSet("foo","bar"), true);
    private static final AppConfigValidator UPDATE_VALIDATOR = new AppConfigValidator(Sets.newHashSet("foo","bar"), false);
    
    @Test
    public void guidRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(UPDATE_VALIDATOR, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(UPDATE_VALIDATOR, appConfig, "label", "is required");
    }
    
    @Test
    public void labelRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "label", "is required");
    }
    
    @Test
    public void studyIdRequired() {
        AppConfig appConfig = AppConfig.create();
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "studyId", "is required");
        
        appConfig.setStudyId("");
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "studyId", "is required");
    }
    
    @Test
    public void criteriaAreRequired() {
        AppConfig appConfig = AppConfig.create();
        
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "criteria", "are required");
    }
    
    @Test
    public void surveyReferencesHaveCreatedOnTimestamps() { 
        AppConfig appConfig = AppConfig.create();
        SurveyReference surveyRef = new SurveyReference("identifier","guid",null);
        appConfig.getSurveyReferences().add(surveyRef);
        
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "surveyReferences[0].createdOn", "is required");
    }
    
    @Test
    public void schemaReferencesHaveRevision() { 
        AppConfig appConfig = AppConfig.create();
        SchemaReference schemaRef = new SchemaReference("guid",null);
        appConfig.getSchemaReferences().add(schemaRef);
        
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "schemaReferences[0].revision", "is required");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("test-study");
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(NEW_VALIDATOR, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: bar, foo");
    }
}
