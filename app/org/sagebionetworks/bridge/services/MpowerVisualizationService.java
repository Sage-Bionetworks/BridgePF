package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.MpowerVisualizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;
import org.sagebionetworks.bridge.validators.MpowerVisualizationValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** mPower visualization service. */
@Component
public class MpowerVisualizationService {
    private static final int MAX_RANGE_DAYS = 45;

    private MpowerVisualizationDao mpowerVisualizationDao;

    /** mPower visualization DAO. */
    @Autowired
    final void setMpowerVisualizationDao(MpowerVisualizationDao mpowerVisualizationDao) {
        this.mpowerVisualizationDao = mpowerVisualizationDao;
    }

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
     *         start date for visualization; if null, defaults to yesterday
     * @param endDate
     *         end date for visualization; if nul, defaults to yesterday
     * @return raw JSON containing visualization data
     */
    public JsonNode getVisualization(String healthCode, LocalDate startDate, LocalDate endDate) {
        // Start and end date default to yesterday's date if not specified.
        if (startDate == null) {
            startDate = DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        }
        if (endDate == null) {
            endDate = DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        }

        // start date and end date are user input. Must validate them.
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("start date " + startDate + " can't be after end date " + endDate);
        }

        Period dateRange = new Period(startDate, endDate, PeriodType.days());
        if (dateRange.getDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days, startDate=" +
                    startDate + ", endDate=" + endDate);
        }

        // Don't need to validate study ID or healthCode. Controller takes care of that for us.

        return mpowerVisualizationDao.getVisualization(healthCode, startDate, endDate);
    }

    /**
     * Writes the visualization to the backing store. See for details:
     * https://sagebionetworks.jira.com/wiki/display/BRIDGE/mPower+Visualization
     *
     * @param visualization
     *         visualization object, which includes visualization JSON data and metadata, must be non-null
     */
    public void writeVisualization(MpowerVisualization visualization) {
        // validate visualization
        if (visualization == null) {
            throw new InvalidEntityException("visualization must be specified");
        }
        Validate.entityThrowingException(MpowerVisualizationValidator.INSTANCE, visualization);

        // call through to DAO
        mpowerVisualizationDao.writeVisualization(visualization);
    }
}
