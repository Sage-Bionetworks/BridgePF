package org.sagebionetworks.bridge.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;

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
}
