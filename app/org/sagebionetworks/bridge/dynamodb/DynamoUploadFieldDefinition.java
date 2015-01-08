package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Dynamo DB implementation of UploadFieldDefinition. While there is nothing specific to Dynamo DB in this class, this
 * class exists to distinguish itself from potential other implementations.
 */
@JsonDeserialize(builder = DynamoUploadFieldDefinition.Builder.class)
public class DynamoUploadFieldDefinition implements UploadFieldDefinition {
    private final @Nonnull String name;
    private final boolean required;
    private final @Nonnull UploadFieldType type;

    /** Private constructor. Construction of a DynamoUploadFieldDefinition should go through the Builder. */
    private DynamoUploadFieldDefinition(@Nonnull String name, boolean required, @Nonnull UploadFieldType type) {
        this.name = name;
        this.required = required;
        this.type = type;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRequired() {
        return required;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadFieldType getType() {
        return type;
    }

    /** Builder for DynamoUploadFieldDefinition */
    public static class Builder {
        private String name;
        private Boolean required;
        private UploadFieldType type;

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getName */
        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#isRequired */
        public Builder withRequired(Boolean required) {
            this.required = required;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getType */
        public Builder withType(UploadFieldType type) {
            this.type = type;
            return this;
        }

        /**
         * Builds and validates a DynamoUploadFieldDefinition. name must be non-null and non-empty. type must be
         * non-null. required may be null and defaults to true. If this is called with invalid fields, it will throw an
         * InvalidEntityException.
         *
         * @return validated DynamoUploadFieldDefinition
         * @throws InvalidEntityException
         *         if called with invalid fields
         */
        public DynamoUploadFieldDefinition build() throws InvalidEntityException {
            if (required == null) {
                required = true;
            }

            DynamoUploadFieldDefinition fieldDef = new DynamoUploadFieldDefinition(name, required, type);
            Validate.entityThrowingException(Validator.INSTANCE, fieldDef);
            return fieldDef;
        }
    }
}
