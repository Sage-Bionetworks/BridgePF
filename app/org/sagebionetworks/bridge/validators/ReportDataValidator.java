package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;

public class ReportDataValidator implements Validator {

    private final Set<String> existingSubstudies;
    
    public ReportDataValidator(ReportIndex index) {
        this.existingSubstudies = (index == null) ? null : index.getSubstudyIds();    
    }
    
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
        if (existingSubstudies != null && data.getSubstudyIds() != null) {
            if (!existingSubstudies.equals(data.getSubstudyIds())) {
                errors.rejectValue("substudyIds", "cannot be changed once created for a report");
            }
        }
        ReportDataKey key = data.getReportDataKey();
        if (key == null) {
            errors.rejectValue("key", "is required");
        } else {
            ReportDataKeyValidator.INSTANCE.validate(key, errors);
        }
    }
}
