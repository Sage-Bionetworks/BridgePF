package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public class NotificationRegistrationValidator implements Validator {

    public static final NotificationRegistrationValidator INSTANCE = new NotificationRegistrationValidator();
    
    public boolean supports(Class<?> clazz) {
        return NotificationRegistration.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        NotificationRegistration registration = (NotificationRegistration)object;
        
        if (isBlank(registration.getHealthCode())) {
            errors.rejectValue("healthCode", "is required");
        }

        switch (registration.getProtocol()) {
            case APPLICATION:
                if (isBlank(registration.getDeviceId())) {
                    errors.rejectValue("deviceId", "is required");
                }
                if (isBlank(registration.getOsName())) {
                    errors.rejectValue("osName", "is required");
                } else if (!OperatingSystem.ALL_OS_SYSTEMS.contains(registration.getOsName())) {
                    errors.rejectValue("osName", "is not a supported platform");
                }
                break;
            case SMS:
                if (isBlank(registration.getEndpoint())) {
                    errors.rejectValue("endpoint", "is required");
                }
                break;
            default:
                errors.rejectValue("protocol", "is not a supported protocol");
                break;
        }
    }
}
