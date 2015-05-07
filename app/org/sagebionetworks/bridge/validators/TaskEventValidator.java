package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class TaskEventValidator implements Validator {

    public static final TaskEventValidator INSTANCE = new TaskEventValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return DynamoTaskEvent.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        DynamoTaskEvent event = (DynamoTaskEvent)object;

        if (isBlank(event.getHealthCode())) {
            errors.rejectValue("healthCode", "cannot be null or blank");
        }
        if (event.getTimestamp() == null) {
            errors.rejectValue("timestamp", "cannot be null");
        }
        if (event.getEventId() == null) {
            errors.rejectValue("eventId", "cannot be null");
        }
    }
}
