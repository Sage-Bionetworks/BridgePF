package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

public class NotificationTopicValidator implements Validator {

    public static final NotificationTopicValidator INSTANCE = new NotificationTopicValidator();
    
    public boolean supports(Class<?> clazz) {
        return NotificationTopic.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        NotificationTopic topic = (NotificationTopic)object;
        
        if (isBlank(topic.getStudyId())) {
            errors.rejectValue("studyId", "is required");
        }

        if (isBlank(topic.getName())) {
            errors.rejectValue("name", "is required");
        }

        if (isBlank(topic.getShortName())) {
            errors.rejectValue("shortName", "is required");
        } else if (topic.getShortName().length() > 10) {
            errors.rejectValue("shortName", "must be 10 characters or less");
        }
    }
}
