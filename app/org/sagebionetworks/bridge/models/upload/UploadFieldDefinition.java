package org.sagebionetworks.bridge.models.upload;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * This class represents a field definition for an upload schema. This could map to a top-level key-value pair in the
 * raw JSON, or to a column in a Synapse table.
 */
@JsonDeserialize(as = DynamoUploadFieldDefinition.class)
public interface UploadFieldDefinition extends BridgeEntity {
    /** The field name. */
    @Nonnull String getName();

    /** True if the field is required to have data, false otherwise. */
    boolean isRequired();

    /**
     * The field's type.
     *
     * @see org.sagebionetworks.bridge.models.upload.UploadFieldType
     */
    @Nonnull UploadFieldType getType();

    /** This class validates the intrinsic properties of the field definition. */
    static class Validator implements org.springframework.validation.Validator {
        /** Singleton instance of this validator. */
        public static final Validator INSTANCE = new Validator();

        /**
         * {@inheritDoc}
         */
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
                errors.rejectValue("uploadFieldDefinition", Validate.CANNOT_BE_NULL);
            } else if (!(target instanceof UploadFieldDefinition)) {
                errors.rejectValue("uploadFieldDefinition", Validate.WRONG_TYPE);
            } else {
                UploadFieldDefinition fieldDef = (UploadFieldDefinition) target;

                if (Strings.isNullOrEmpty(fieldDef.getName())) {
                    errors.rejectValue("name", Validate.CANNOT_BE_BLANK);
                }

                if (fieldDef.getType() == null) {
                    errors.rejectValue("type", Validate.CANNOT_BE_NULL);
                }
            }
        }
    }
}
