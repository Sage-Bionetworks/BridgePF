package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class HealthDataRecordValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return HealthDataRecord.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        HealthDataRecord record = (HealthDataRecord)object;
        
        /* Totally legal, actually. This is only invalid for searchers.
        if (record.getStartDate() <= 0) {
            errors.rejectValue("startDate", "required and cannot be <= 0");
        }
        if (record.getEndDate() <= 0) {
            errors.rejectValue("endDate", "required and cannot be <= 0");
        }
        if (record.getEndDate() < record.getStartDate()) {
            errors.rejectValue("endDate", "earlier than the startDate: " + record.getEndDate());
        }
        */
        if (record.getRecordId() == null) {
            errors.rejectValue("recordId", "null");
        }
    }

}
