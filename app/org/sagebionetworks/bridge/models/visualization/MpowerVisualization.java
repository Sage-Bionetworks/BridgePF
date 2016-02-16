package org.sagebionetworks.bridge.models.visualization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualization;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Generic interface mPower visualization, for use outside of the DDB layer. Individual data points are stored as raw
 * JSON in the visualization field for rapid development. When we generalize this, we can restructure this class as
 * needed.
 */
@BridgeTypeName("MpowerVisualization")
@JsonDeserialize(as = DynamoMpowerVisualization.class)
public interface MpowerVisualization extends BridgeEntity {
    /** Date for this data point. */
    LocalDate getDate();

    /** @see #getDate */
    void setDate(LocalDate date);

    /** Health code of user for this data point. */
    String getHealthCode();

    /** @see #getHealthCode */
    void setHealthCode(String healthCode);

    /** Raw JSON data of this data point. */
    JsonNode getVisualization();

    /** @see #getVisualization */
    void setVisualization(JsonNode visualization);
}
