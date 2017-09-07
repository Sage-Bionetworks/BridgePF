package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;

public class ReportDataKeyValidator implements Validator {

    public static final ReportDataKeyValidator INSTANCE = new ReportDataKeyValidator();
    private ReportDataKeyValidator() {}
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ReportDataKey.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ReportDataKey key = (ReportDataKey)object;
        
        if (key.getStudyId() == null) {
            errors.rejectValue("studyId", "is required");
        }
        if (key.getReportType() == null) {
            errors.rejectValue("reportType", "is required");
        }
        if (isBlank(key.getHealthCode()) && key.getReportType() == ReportType.PARTICIPANT) {
            errors.rejectValue("healthCode", "is required for participant reports");
        }
        if (isBlank(key.getIdentifier())) {
            errors.rejectValue("identifier", "cannot be missing or blank");
        } else if (!key.getIdentifier().matches(BridgeConstants.SYNAPSE_IDENTIFIER_PATTERN)) {
            errors.rejectValue("identifier", "can only contain letters, numbers, underscore and dash");
        }
    }

}
