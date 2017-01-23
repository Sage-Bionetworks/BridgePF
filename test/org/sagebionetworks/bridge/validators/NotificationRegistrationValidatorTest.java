package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.NotificationRegistrationValidator.INSTANCE;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public class NotificationRegistrationValidatorTest {

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
        Validate.entityThrowingException(INSTANCE, registration);
    }
    
    @Test
    public void healthCodeRequired() {
        registration.setHealthCode(null);
        assertValidatorMessage(INSTANCE, registration, "healthCode", " is required");
    }
    
    @Test
    public void deviceIdRequired() {
        registration.setDeviceId(null);
        assertValidatorMessage(INSTANCE, registration, "deviceId", " is required");
    }
    
    @Test
    public void osNameRequired() {
        registration.setOsName(null);
        assertValidatorMessage(INSTANCE, registration, "osName", " is required");
    }
    
    @Test
    public void osNameUnknown() {
        registration.setOsName("Not good");
        assertValidatorMessage(INSTANCE, registration, "osName", " is not a supported platform");
    }
}
