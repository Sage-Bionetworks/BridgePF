package org.sagebionetworks.bridge.validators;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.reports.ReportData;

public class ReportDataValidator implements Validator {

    public static final ReportDataValidator INSTANCE = new ReportDataValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ReportData.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ReportData data = (ReportData)object;
        
        if (data.getLocalDate() != null && data.getDateTime() != null) {
            errors.reject("must include a localDate or dateTime, but not both");
        } else if (data.getLocalDate() == null && data.getDateTime() == null) {
            errors.reject("must include a localDate or dateTime");
        }
    }
}
