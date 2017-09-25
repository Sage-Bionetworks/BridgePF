package org.sagebionetworks.bridge.models.upload;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

/**
 * This class represents a field definition for an upload schema. This could map to a top-level key-value pair in the
 * raw JSON, or to a column in a Synapse table.
 */
@JsonDeserialize(builder = UploadFieldDefinition.Builder.class)
public final class UploadFieldDefinition {
    private final Boolean allowOtherChoices;
    private final String fileExtension;
    private final String mimeType;
    private final Integer maxLength;
    private final List<String> multiChoiceAnswerList;
    private final String name;
    private final boolean required;
    private final UploadFieldType type;
    private final Boolean unboundedText;

    /** Private constructor. Construction of a UploadFieldDefinition should go through the Builder. */
    private UploadFieldDefinition(Boolean allowOtherChoices, String fileExtension,
            String mimeType, Integer maxLength, List<String> multiChoiceAnswerList,
            String name, boolean required, UploadFieldType type, Boolean unboundedText) {
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

    /**
     * Used for MULTI_CHOICE. True if the multi-choice field allows an "other" answer with user freeform text. This
     * tells BridgeEX to reserve an "other" column for this field. Can be null, so that the number of field parameters
     * doesn't explode.
     */
    public Boolean getAllowOtherChoices() {
        return allowOtherChoices;
    }

    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to preserve the file extension as a quality-of-life
     * improvement. Optional, defaults to ".tmp".
     */
    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to mark a Synapse file handle with the correct MIME
     * type as a quality-of-life improvement. Optional, defaults to "application/octet-stream".
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Used for STRING, SINGLE_CHOICE, and INLINE_JSON_BLOB types. This is a hint for BridgeEX to create a Synapse
     * column with the right width.
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * <p>
     * Used for MULTI_CHOICE types. This lists all valid answers for this field. It is used by BridgeEX to create the
     * Synapse table columns for MULTI_CHOICE fields. This is a list because order matters, in terms of Synapse
     * column order. Must be specified if the field type is a MULTI_CHOICE.
     * </p>
     * <p>
     * For schemas generated from surveys, this list will be the "value" in the survey question option, or the "label"
     * if value is not specified.
     * </p>
     */
    public List<String> getMultiChoiceAnswerList() {
        return multiChoiceAnswerList;
    }

    /** The field name. */
    public String getName() {
        return name;
    }

    /** True if the field is required to have data, false otherwise. */
    public boolean isRequired() {
        return required;
    }

    /**
     * The field's type.
     *
     * @see org.sagebionetworks.bridge.models.upload.UploadFieldType
     */
    public UploadFieldType getType() {
        return type;
    }

    /**
     * True if this field is a text-field with unbounded length. (Only applies to fields that are serialized as text,
     * such as INLINE_JSON_BLOB, SINGLE_CHOICE, or STRING. Can be null, so that the number of field parameters doesn't
     * explode.
     */
    public Boolean isUnboundedText() {
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
        UploadFieldDefinition that = (UploadFieldDefinition) o;
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

    /** Builder for UploadFieldDefinition */
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
         * Builds and validates a UploadFieldDefinition. name must be non-null and non-empty. type must be
         * non-null. required may be null and defaults to true. If this is called with invalid fields, it will throw an
         * InvalidEntityException.
         *
         * @return validated UploadFieldDefinition
         * @throws InvalidEntityException
         *         if called with invalid fields
         */
        public UploadFieldDefinition build() throws InvalidEntityException {
            if (required == null) {
                required = true;
            }

            // If the answer list was specified, make an immutable copy.
            List<String> multiChoiceAnswerListCopy;
            if (multiChoiceAnswerList != null) {
                multiChoiceAnswerListCopy = ImmutableList.copyOf(multiChoiceAnswerList);
            } else {
                multiChoiceAnswerListCopy = ImmutableList.of();
            }

            return new UploadFieldDefinition(allowOtherChoices, fileExtension, mimeType, maxLength,
                    multiChoiceAnswerListCopy, name, required, type, unboundedText);
        }
    }
}
