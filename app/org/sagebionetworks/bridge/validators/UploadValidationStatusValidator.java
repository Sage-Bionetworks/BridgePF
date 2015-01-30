package org.sagebionetworks.bridge.validators;

import com.google.common.base.Strings;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;

/** Validator for {@link org.sagebionetworks.bridge.models.upload.UploadValidationStatus}. */
public class UploadValidationStatusValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadValidationStatusValidator INSTANCE = new UploadValidationStatusValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return UploadValidationStatus.class.isAssignableFrom(clazz);
    }

    /**
     * Validates the given object as a valid UploadValidationStatus instance. This will flag errors if the given value
     * is null, not an UploadValidationStatus, if id is null or empty, if messageList is null or contains null or empty
     * messages, or if status is null.
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("uploadValidationStatus", Validate.CANNOT_BE_NULL);
        } else if (!(target instanceof UploadValidationStatus)) {
            errors.rejectValue("uploadValidationStatus", Validate.WRONG_TYPE);
        } else {
            UploadValidationStatus validationStatus = (UploadValidationStatus) target;

            if (Strings.isNullOrEmpty(validationStatus.getId())) {
                errors.rejectValue("id", Validate.CANNOT_BE_BLANK);
            }

            // no need to validate messageList, since the builder validated that for us before stuffing it into an
            // ImmutableList

            if (validationStatus.getStatus() == null) {
                errors.rejectValue("status", Validate.CANNOT_BE_NULL);
            }
        }
    }
}
