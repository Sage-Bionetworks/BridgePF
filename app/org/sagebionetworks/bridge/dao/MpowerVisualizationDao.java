package org.sagebionetworks.bridge.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;

/** Abstract DAO for mPower visualization. */
public interface MpowerVisualizationDao {
    /**
     * <p>
     * Get the visualization for the given date range. We use raw JSON to allow for rapid development. When we need to
     * genericize and harden this, we can refactor this class as necessary.
     * </p>
     * <p>
     * See https://sagebionetworks.jira.com/wiki/display/BRIDGE/mPower+Visualization for details.
     * </p>
     *
     * @param healthCode
     *         user's health code
     * @param startDate
     *         start date for visualization
     * @param endDate
     *         end date for visualization
     * @return raw JSON containing visualization data
     */
    JsonNode getVisualization(String healthCode, LocalDate startDate, LocalDate endDate);

    /**
     * Writes the visualization to the backing store. See for details:
     * https://sagebionetworks.jira.com/wiki/display/BRIDGE/mPower+Visualization
     *
     * @param visualization
     *         visualization object, which includes visualization JSON data and metadata
     */
    void writeVisualization(MpowerVisualization visualization);
}
