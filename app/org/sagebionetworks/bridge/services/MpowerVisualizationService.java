package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.MpowerVisualizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;

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
     * @param startDateStr
     *         start date for visualization
     * @param endDateStr
     *         end date for visualization
     * @return raw JSON containing visualization data
     */
    public JsonNode getVisualization(String healthCode, String startDateStr, String endDateStr) {
        // start date and end date are user input. Must validate them.
        // Note that we parse the start and end date strings here and not in the controller. This is to consolidate as
        // much logic as possible into the service, which is much easier to test than the controller.
        LocalDate startDate = parseDateHelper(startDateStr);
        LocalDate endDate = parseDateHelper(endDateStr);

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("start date " + startDateStr + " can't be after end date " + endDateStr);
        }

        Period dateRange = new Period(startDate, endDate, PeriodType.days());
        if (dateRange.getDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days, startDate=" +
                    startDateStr + ", endDate=" + endDateStr);
        }

        // Don't need to validate study ID or healthCode. Controller takes care of that for us.

        return mpowerVisualizationDao.getVisualization(healthCode, startDate, endDate);
    }

    // Helper method to parse dates. If the date isn't specified, it defaults to yesterday's date in the local timezone
    private static LocalDate parseDateHelper(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        } else {
            try {
                return DateUtils.parseCalendarDate(dateStr);
            } catch (RuntimeException ex) {
                throw new BadRequestException("invalid date " + dateStr);
            }
        }
    }
}
