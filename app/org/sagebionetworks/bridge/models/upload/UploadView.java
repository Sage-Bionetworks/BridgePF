package org.sagebionetworks.bridge.models.upload;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

@BridgeTypeName("Upload")
public final class UploadView {

    @JsonUnwrapped
    final Upload upload;
    @JsonProperty("schemaId")
    final String schemaId;
    @JsonProperty("schemaRevision")
    final Integer schemaRevision;
    
    private UploadView(Upload upload, String schemaId, Integer schemaRevision) {
        this.upload = upload;
        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
    }
    
    public Upload getUpload() {
        return upload;
    }
    
    public String getSchemaId() {
        return schemaId;
    }
    
    public Integer getSchemaRevision() {
        return schemaRevision;
    }
    
    public static class Builder {
        private Upload upload;
        private String schemaId;
        private Integer schemaRevision;
        
        public Builder withUpload(Upload upload) {
            this.upload = upload;
            return this;
        }
        public Builder withSchemaId(String schemaId) {
            this.schemaId = schemaId;
            return this;
        }
        public Builder withSchemaRevision(Integer schemaRevision) {
            this.schemaRevision = schemaRevision;
            return this;
        }
        public UploadView build() {
            return new UploadView(upload, schemaId, schemaRevision);
        }
    }

}
