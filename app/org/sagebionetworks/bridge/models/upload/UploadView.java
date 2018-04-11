package org.sagebionetworks.bridge.models.upload;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@BridgeTypeName("Upload")
@JsonDeserialize(builder = UploadView.Builder.class)
public final class UploadView {

    @JsonUnwrapped
    final Upload upload;
    final String schemaId;
    final Integer schemaRevision;
    final HealthDataRecord.ExporterStatus healthRecordExporterStatus;
    final HealthDataRecord record;
    
    private UploadView(Upload upload, String schemaId, Integer schemaRevision, HealthDataRecord.ExporterStatus status,
            HealthDataRecord record) {
        this.upload = upload;
        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
        this.healthRecordExporterStatus = status;
        this.record = record;
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
    
    public HealthDataRecord getHealthData() {
        return record;
    }
    
    public static class Builder {
        private Upload upload;
        private String schemaId;
        private Integer schemaRevision;
        private HealthDataRecord.ExporterStatus healthRecordExporterStatus;
        private HealthDataRecord record;
        
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
        public Builder withHealthDataRecord(HealthDataRecord record) {
            this.record = record;
            return this;
        }
        public UploadView build() {
            return new UploadView(upload, schemaId, schemaRevision, healthRecordExporterStatus, record);
        }
    }

}
