package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;

import com.google.common.collect.Sets;

public class AppConfigValidatorTest {

    private static final AppConfigValidator VALIDATOR = new AppConfigValidator(Sets.newHashSet("foo","bar"));
    
    @Test
    public void studyIdRequired() {
        AppConfig appConfig = AppConfig.create();
        
        assertValidatorMessage(VALIDATOR, appConfig, "studyId", "is required");
    }
    
    @Test
    public void criteriaAreRequired() {
        AppConfig appConfig = AppConfig.create();
        
        assertValidatorMessage(VALIDATOR, appConfig, "criteria", "are required");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId("test-study");
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(VALIDATOR, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: bar, foo");
    }
}
