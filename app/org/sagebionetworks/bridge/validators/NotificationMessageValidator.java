package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class NotificationMessageValidator implements Validator {
    
    public static final NotificationMessageValidator INSTANCE = new NotificationMessageValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return NotificationMessage.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        NotificationMessage message = (NotificationMessage)object;
        
        if (isBlank(message.getSubject())) {
            errors.rejectValue("subject", "is required");
        }
        if (isBlank(message.getMessage())) {
            errors.rejectValue("message", "is required");
        }
    }

}
