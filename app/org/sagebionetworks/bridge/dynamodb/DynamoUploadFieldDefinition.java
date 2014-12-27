package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

@JsonDeserialize(builder = DynamoUploadFieldDefinition.Builder.class)
public class DynamoUploadFieldDefinition implements UploadFieldDefinition {
    private final String name;
    private final boolean required;
    private final UploadFieldType type;

    private DynamoUploadFieldDefinition(String name, boolean required, UploadFieldType type) {
        this.name = name;
        this.required = required;
        this.type = type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public UploadFieldType getType() {
        return type;
    }

    public static class Builder {
        private String name;
        private Boolean required;
        private UploadFieldType type;

        public String getName() {
            return name;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Boolean getRequired() {
            return required;
        }

        public Builder setRequired(Boolean required) {
            this.required = required;
            return this;
        }

        public UploadFieldType getType() {
            return type;
        }

        public Builder withType(UploadFieldType type) {
            this.type = type;
            return this;
        }

        public UploadFieldDefinition build() {
            // TODO validate
            return new DynamoUploadFieldDefinition(name, required, type);
        }
    }
}
