package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.NotificationRegistrationValidator.INSTANCE;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public class NotificationRegistrationValidatorTest {

    private static final String OS_NAME = "Android";
    
    private NotificationRegistration registration;
    
    @Before
    public void before() {
        registration = TestUtils.getNotificationRegistration();
        registration.setOsName(OS_NAME);
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

    @Test
    public void validSmsRegistration() {
        Validate.entityThrowingException(INSTANCE, makeValidSmsRegistration());
    }

    @Test
    public void smsRegistrationHealthCodeRequired() {
        NotificationRegistration registration = makeValidSmsRegistration();
        registration.setHealthCode(null);
        assertValidatorMessage(INSTANCE, registration, "healthCode", " is required");
    }

    @Test
    public void smsRegistrationEndpointRequired() {
        NotificationRegistration registration = makeValidSmsRegistration();
        registration.setEndpoint(null);
        assertValidatorMessage(INSTANCE, registration, "endpoint", " is required");
    }

    private static NotificationRegistration makeValidSmsRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode("health-code");
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint("+14255550123");
        return registration;
    }
}
