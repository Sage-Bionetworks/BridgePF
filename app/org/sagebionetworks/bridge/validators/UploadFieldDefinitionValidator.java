package org.sagebionetworks.bridge.validators;

import com.google.common.base.Strings;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;

/** Validator for {@link org.sagebionetworks.bridge.models.upload.UploadFieldDefinition} */
public class UploadFieldDefinitionValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadFieldDefinitionValidator INSTANCE = new UploadFieldDefinitionValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return UploadFieldDefinition.class.isAssignableFrom(clazz);
    }

    /**
     * Validates the given object as a valid UploadFieldDefinition instance. This will flag errors if the given
     * value is null, not an UploadFieldDefinition, if the name is null or empty, or if the type is null.
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("uploadFieldDefinition", "cannot be null");
        } else if (!(target instanceof UploadFieldDefinition)) {
            errors.rejectValue("uploadFieldDefinition", "is the wrong type");
        } else {
            UploadFieldDefinition fieldDef = (UploadFieldDefinition) target;

            if (Strings.isNullOrEmpty(fieldDef.getName())) {
                errors.rejectValue("name", "is required");
            }

            if (fieldDef.getType() == null) {
                errors.rejectValue("type", "is required");
            }
        }
    }
}
