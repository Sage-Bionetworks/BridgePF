package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Dynamo DB implementation of UploadFieldDefinition. While there is nothing specific to Dynamo DB in this class, this
 * class exists to distinguish itself from potential other implementations.
 */
@JsonDeserialize(builder = DynamoUploadFieldDefinition.Builder.class)
public final class DynamoUploadFieldDefinition implements UploadFieldDefinition {
    private final @Nullable Boolean allowOtherChoices;
    private final @Nullable String fileExtension;
    private final @Nullable String mimeType;
    private final @Nullable Integer maxLength;
    private final @Nullable List<String> multiChoiceAnswerList;
    private final @Nonnull String name;
    private final boolean required;
    private final @Nonnull UploadFieldType type;
    private final @Nonnull Boolean unboundedText;

    /** Private constructor. Construction of a DynamoUploadFieldDefinition should go through the Builder. */
    private DynamoUploadFieldDefinition(@Nullable Boolean allowOtherChoices, @Nullable String fileExtension,
            @Nullable String mimeType, @Nullable Integer maxLength, @Nullable List<String> multiChoiceAnswerList,
            @Nonnull String name, boolean required, @Nonnull UploadFieldType type, @Nonnull Boolean unboundedText) {
        this.allowOtherChoices = allowOtherChoices;
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
        this.maxLength = maxLength;
        this.multiChoiceAnswerList = multiChoiceAnswerList;
        this.name = name;
        this.required = required;
        this.type = type;
        this.unboundedText = unboundedText;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Boolean getAllowOtherChoices() {
        return allowOtherChoices;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getFileExtension() {
        return fileExtension;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getMimeType() {
        return mimeType;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Integer getMaxLength() {
        return maxLength;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getMultiChoiceAnswerList() {
        return multiChoiceAnswerList;
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
    public @Nonnull Boolean isUnboundedText() {
        return unboundedText;
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
        return required == that.required &&
                Objects.equals(allowOtherChoices, that.allowOtherChoices) &&
                Objects.equals(fileExtension, that.fileExtension) &&
                Objects.equals(mimeType, that.mimeType) &&
                Objects.equals(maxLength, that.maxLength) &&
                Objects.equals(multiChoiceAnswerList, that.multiChoiceAnswerList) &&
                Objects.equals(name, that.name) &&
                type == that.type &&
                Objects.equals(unboundedText, that.unboundedText);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(allowOtherChoices, fileExtension, mimeType, maxLength,
                multiChoiceAnswerList, name, required, type, unboundedText);
    }

    /** Builder for DynamoUploadFieldDefinition */
    public static class Builder {
        private Boolean allowOtherChoices;
        private String fileExtension;
        private String mimeType;
        private Integer maxLength;
        private List<String> multiChoiceAnswerList;
        private String name;
        private Boolean required;
        private UploadFieldType type;
        private Boolean unboundedText;

        /** Copies attributes from the given field definition. */
        public Builder copyOf(UploadFieldDefinition other) {
            this.allowOtherChoices = other.getAllowOtherChoices();
            this.fileExtension = other.getFileExtension();
            this.mimeType = other.getMimeType();
            this.maxLength = other.getMaxLength();
            this.multiChoiceAnswerList = other.getMultiChoiceAnswerList();
            this.name = other.getName();
            this.required = other.isRequired();
            this.type = other.getType();
            this.unboundedText = other.isUnboundedText();
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getAllowOtherChoices */
        public Builder withAllowOtherChoices(Boolean allowOtherChoices) {
            this.allowOtherChoices = allowOtherChoices;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getFileExtension */
        public Builder withFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMimeType */
        public Builder withMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMaxLength */
        public Builder withMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMultiChoiceAnswerList */
        @JsonSetter
        public Builder withMultiChoiceAnswerList(List<String> multiChoiceAnswerList) {
            this.multiChoiceAnswerList = multiChoiceAnswerList;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMultiChoiceAnswerList */
        public Builder withMultiChoiceAnswerList(String... multiChoiceAnswerList) {
            this.multiChoiceAnswerList = ImmutableList.copyOf(multiChoiceAnswerList);
            return this;
        }

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

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#isUnboundedText */
        public Builder withUnboundedText(Boolean unboundedText) {
            this.unboundedText = unboundedText;
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

            // If the answer list was specified, make an immutable copy.
            List<String> multiChoiceAnswerListCopy = null;
            if (multiChoiceAnswerList != null) {
                multiChoiceAnswerListCopy = ImmutableList.copyOf(multiChoiceAnswerList);
            }

            return new DynamoUploadFieldDefinition(allowOtherChoices, fileExtension, mimeType, maxLength,
                    multiChoiceAnswerListCopy, name, required, type, unboundedText);
        }
    }
}
