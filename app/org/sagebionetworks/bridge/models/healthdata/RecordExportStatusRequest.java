package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.json.JsonUtils;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * POJO to wrap synapse exporter status infomation, including a list of record ids and a exporter status
 */
public class RecordExportStatusRequest {

    private @Nonnull List<String> recordIds;
    private @Nonnull HealthDataRecord.ExporterStatus synapseExporterStatus;

    public List<String> getRecordIds() { return this.recordIds; }

    public HealthDataRecord.ExporterStatus getSynapseExporterStatus() { return this.synapseExporterStatus; }

    public void setRecordIds(List<String> recordIds) { this.recordIds = recordIds; }

    public void setSynapseExporterStatus(HealthDataRecord.ExporterStatus synapseExporterStatus) { this.synapseExporterStatus = synapseExporterStatus; }
}
