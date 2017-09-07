package org.sagebionetworks.bridge.validators;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

public class ReportDataValidator implements Validator {

    public static final ReportDataValidator INSTANCE = new ReportDataValidator();
    private ReportDataValidator() {}
    
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
        if (data.getData() == null) {
            errors.rejectValue("data", "is required");
        }
        ReportDataKey key = data.getReportDataKey();
        if (key == null) {
            errors.rejectValue("key", "is required");
        } else {
            ReportDataKeyValidator.INSTANCE.validate(key, errors);
        }
    }
}
