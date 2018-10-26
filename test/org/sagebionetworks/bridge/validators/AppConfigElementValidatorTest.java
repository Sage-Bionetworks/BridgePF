package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

public class AppConfigElementValidatorTest {
    private static final AppConfigElementValidator VALIDATOR = AppConfigElementValidator.INSTANCE;
    
    private AppConfigElement element;
    
    @Before
    public void before() {
        element = TestUtils.getAppConfigElement();
    }
    
    @Test
    public void valid() {
        Validate.entityThrowingException(VALIDATOR, element);
    }
    
    @Test
    public void idRequired() {
        element.setId(null);
        assertValidatorMessage(VALIDATOR, element, "id", "is required");
        
        element.setId("");
        assertValidatorMessage(VALIDATOR, element, "id", "is required");
    }
    
    @Test
    public void idInvalid() {
        element.setId("@bad");
        assertValidatorMessage(VALIDATOR, element, "id", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }
    
    @Test
    public void revisionRequired() {
        element.setRevision(null);
        assertValidatorMessage(VALIDATOR, element, "revision", "is required");
        
        element.setRevision(-3L);
        assertValidatorMessage(VALIDATOR, element, "revision", "must be positive");
        
        element.setRevision(0L);
        assertValidatorMessage(VALIDATOR, element, "revision", "must be positive");
    }
    
    @Test
    public void dataRequired() {
        element.setData(null);
        assertValidatorMessage(VALIDATOR, element, "data", "is required");
    }
}
