package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public class NotificationRegistrationValidator implements Validator {

    public boolean supports(Class<?> clazz) {
        return NotificationRegistration.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        NotificationRegistration registration = (NotificationRegistration)object;
        
        if (isBlank(registration.getHealthCode())) {
            errors.rejectValue("healthCode", "is required");
        }
        if (isBlank(registration.getDeviceId())) {
            errors.rejectValue("deviceId", "is required");
        }
        if (isBlank(registration.getOsName())) {
            errors.rejectValue("deviceId", "is required");   
        } else if () {
            
        }
    }

}
