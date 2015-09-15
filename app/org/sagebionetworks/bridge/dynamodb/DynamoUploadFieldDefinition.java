package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Objects;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Dynamo DB implementation of UploadFieldDefinition. While there is nothing specific to Dynamo DB in this class, this
 * class exists to distinguish itself from potential other implementations.
 */
@JsonDeserialize(builder = DynamoUploadFieldDefinition.Builder.class)
public final class DynamoUploadFieldDefinition implements UploadFieldDefinition {
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

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DynamoUploadFieldDefinition that = (DynamoUploadFieldDefinition) o;
        return Objects.equal(required, that.required) &&
                Objects.equal(name, that.name) &&
                Objects.equal(type, that.type);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hashCode(name, required, type);
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
            return new DynamoUploadFieldDefinition(name, required, type);
        }
    }
}
