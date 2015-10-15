package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ActivityEventValidator implements Validator {

    public static final ActivityEventValidator INSTANCE = new ActivityEventValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return DynamoActivityEvent.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        DynamoActivityEvent event = (DynamoActivityEvent)object;

        if (isBlank(event.getHealthCode())) {
            errors.rejectValue("healthCode", "cannot be null or blank");
        }
        if (event.getTimestamp() == null) {
            errors.rejectValue("timestamp", "cannot be null");
        }
        if (event.getEventId() == null) {
            errors.rejectValue("eventId", "cannot be null (may be missing object or event type)");
        } else if (event.getEventId().endsWith(":answered") && isBlank(event.getAnswerValue())) {
            errors.rejectValue("answerValue", "cannot be null or blank if the event indicates the answer to a survey");
        }
    }
}
