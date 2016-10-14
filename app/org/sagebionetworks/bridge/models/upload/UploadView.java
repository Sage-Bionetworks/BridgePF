package org.sagebionetworks.bridge.models.upload;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@BridgeTypeName("Upload")
public final class UploadView {

    @JsonUnwrapped
    final Upload upload;
    @JsonProperty("schemaId")
    final String schemaId;
    @JsonProperty("schemaRevision")
    final Integer schemaRevision;
    @JsonProperty("healthRecordExporterStatus")
    final HealthDataRecord.ExporterStatus healthRecordExporterStatus;
    
    private UploadView(Upload upload, String schemaId, Integer schemaRevision, HealthDataRecord.ExporterStatus status) {
        this.upload = upload;
        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
        this.healthRecordExporterStatus = status;
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

    public HealthDataRecord.ExporterStatus getHealthRecordExporterStatus() {
        return healthRecordExporterStatus;
    }
    
    public static class Builder {
        private Upload upload;
        private String schemaId;
        private Integer schemaRevision;
        private HealthDataRecord.ExporterStatus healthRecordExporterStatus;
        
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
        public Builder withHealthRecordExporterStatus(HealthDataRecord.ExporterStatus status) {
            this.healthRecordExporterStatus = status;
            return this;
        }
        public UploadView build() {
            return new UploadView(upload, schemaId, schemaRevision, healthRecordExporterStatus);
        }
    }

}
