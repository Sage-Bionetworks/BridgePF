package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.List;

/** Validator for {@link org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest}. */
public class RecordExportStatusRequestValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return RecordExportStatusRequest.class.isAssignableFrom(clazz);
    }

    /**
     * <p>
     * Validates the given object as a valid RecordExportStatusRequest instance. This will flag errors in the following
     * conditions:
     * <ul>
     * <li>recordIds is null or empty</li>
     * <li>synapseExporterStatus is null</li>
     * </ul>
     * </p>
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("RecordExportStatusRequest", Validate.CANNOT_BE_NULL);
        } else if (!(target instanceof RecordExportStatusRequest)) {
            errors.rejectValue("RecordExportStatusRequest", Validate.WRONG_TYPE);
        } else {
            RecordExportStatusRequest record = (RecordExportStatusRequest) target;

            // recordIds
            List<String> recordIds = record.getRecordIds();
            if (recordIds == null) {
                errors.rejectValue("recordIds", Validate.CANNOT_BE_NULL);
            }

            if (recordIds.isEmpty()) {
                errors.rejectValue("recordIds", Validate.CANNOT_BE_BLANK);
            }

            // synapseExporterStatus
            HealthDataRecord.ExporterStatus synapseExporterStatus = record.getSynapseExporterStatus();
            if (synapseExporterStatus == null) {
                errors.rejectValue("synapseExporterStatus", Validate.CANNOT_BE_NULL);
            }


        }
    }
}
