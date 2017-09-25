package org.sagebionetworks.bridge.validators;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;

/** Validator for HealthDataSubmission. */
public class HealthDataSubmissionValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final HealthDataSubmissionValidator INSTANCE = new HealthDataSubmissionValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return HealthDataSubmission.class.isAssignableFrom(clazz);
    }

    /** Validates a health data submission. All fields except metadata are required. */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("healthDataSubmission", "cannot be null");
        } else if (!(target instanceof HealthDataSubmission)) {
            errors.rejectValue("healthDataSubmission", "is the wrong type");
        } else {
            HealthDataSubmission healthDataSubmission = (HealthDataSubmission) target;

            // appVersion
            if (StringUtils.isBlank(healthDataSubmission.getAppVersion())) {
                errors.rejectValue("appVersion", "is required");
            }

            // createdOn
            if (healthDataSubmission.getCreatedOn() == null) {
                errors.rejectValue("createdOn", "is required");
            }

            // data - Must be non-null and an ObjectNode.
            JsonNode data = healthDataSubmission.getData();
            if (data == null || data.isNull()) {
                errors.rejectValue("data", "is required");
            } else if (!data.isObject()) {
                errors.rejectValue("data", "must be an object node");
            }

            // metadata is optional, but if specified, it must be an object node.
            JsonNode metadata = healthDataSubmission.getMetadata();
            if (metadata != null && !metadata.isObject()) {
                errors.rejectValue("metadata", "must be an object node");
            }

            // phoneInfo
            if (StringUtils.isBlank(healthDataSubmission.getPhoneInfo())) {
                errors.rejectValue("phoneInfo", "is required");
            }

            // HealthDataSubmission must have schemaId/Revision or surveyGuid/CreatedOn.
            int sources = 0;

            String schemaId = healthDataSubmission.getSchemaId();
            Integer schemaRev = healthDataSubmission.getSchemaRevision();
            if (schemaId != null && schemaRev != null) {
                sources++;
            }

            String surveyGuid = healthDataSubmission.getSurveyGuid();
            DateTime surveyCreatedOn = healthDataSubmission.getSurveyCreatedOn();
            if (surveyGuid != null && surveyCreatedOn != null) {
                sources++;
            }

            if (sources != 1) {
                errors.rejectValue("healthDataSubmission", "must have either schemaId/Revision or " +
                        "surveyGuid/CreatedOn but not both");
            }

            // schemaId
            if (schemaId != null && StringUtils.isBlank(schemaId)) {
                errors.rejectValue("schemaId", "can't be empty or blank");
            }

            // schemaRevision - must be positive
            if (schemaRev != null && schemaRev <= 0) {
                errors.rejectValue("schemaRevision", "can't be zero or negative");
            }

            // surveyGuid
            if (surveyGuid != null && StringUtils.isBlank(surveyGuid)) {
                errors.rejectValue("surveyGuid", "can't be empty or blank");
            }
        }
    }
}
