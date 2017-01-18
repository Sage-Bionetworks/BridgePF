package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
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
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
    }
    
    @Test
    public void healthCodeRequired() {
        registration.setHealthCode(null);
        testError("healthCode", " is required");
    }
    
    @Test
    public void deviceIdRequired() {
        registration.setDeviceId(null);
        testError("deviceId", " is required");
    }
    
    @Test
    public void osNameRequired() {
        registration.setOsName(null);
        testError("osName", " is required");
    }
    
    @Test
    public void osNameUnknown() {
        registration.setOsName("Not good");
        testError("osName", " is not a supported platform");
    }
    
    private void testError(String fieldName, String error) {
        try {
            Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(fieldName+error, e.getErrors().get(fieldName).get(0));
        }
    }
}
