package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public class NotificationRegistrationValidatorTest {

    private static final Validator VALIDATOR = NotificationRegistrationValidator.INSTANCE;
    private static final String HEALTH_CODE = "ABC";
    private static final String DEVICE_ID = "MNO-PQR-STU-VWX";
    private static final String OS_NAME = "Android";
    
    private NotificationRegistration registration;
    
    @Before
    public void before() {
        registration = NotificationRegistration.create();
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OS_NAME);
        registration.setHealthCode(HEALTH_CODE);
    }
    
    @Test
    public void isValid() {
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
    }
    
    @Test
    public void healthCodeRequired() {
        registration.setHealthCode(null);
        assertValidatorMessage(VALIDATOR, registration, "healthCode", " is required");
    }
    
    @Test
    public void deviceIdRequired() {
        registration.setDeviceId(null);
        assertValidatorMessage(VALIDATOR, registration, "deviceId", " is required");
    }
    
    @Test
    public void osNameRequired() {
        registration.setOsName(null);
        assertValidatorMessage(VALIDATOR, registration, "osName", " is required");
    }
    
    @Test
    public void osNameUnknown() {
        registration.setOsName("Not good");
        assertValidatorMessage(VALIDATOR, registration, "osName", " is not a supported platform");
    }
}
