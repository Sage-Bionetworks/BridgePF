package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.models.BridgeEntity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * POJO to wrap synapse exporter status information, including a list of record ids and a exporter status
 */
public class RecordExportStatusRequest implements BridgeEntity {

    private @Nonnull List<String> recordIds;
    private @Nonnull HealthDataRecord.ExporterStatus synapseExporterStatus;

    public List<String> getRecordIds() { return this.recordIds; }

    public HealthDataRecord.ExporterStatus getSynapseExporterStatus() { return this.synapseExporterStatus; }

    public void setRecordIds(List<String> recordIds) { this.recordIds = recordIds; }

    public void setSynapseExporterStatus(HealthDataRecord.ExporterStatus synapseExporterStatus) { this.synapseExporterStatus = synapseExporterStatus; }
}
