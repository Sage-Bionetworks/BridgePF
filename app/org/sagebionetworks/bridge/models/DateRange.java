package org.sagebionetworks.bridge.models;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

/** Model object representing a date range, which includes start date and end date as calendar dates (YYYY-MM-DD). */
public class DateRange implements BridgeEntity {
    private final @Nonnull LocalDate startDate;
    private final @Nonnull LocalDate endDate;

    /**
     * DateRange constructor. Also validates that startDate and endDate are specified and that startDate isn't after
     * endDate.
     *
     * @param startDate
     *         start date, must be non-null
     * @param endDate
     *         end date, must be non-null
     */
    public DateRange(@Nonnull @JsonProperty("startDate") LocalDate startDate,
            @Nonnull @JsonProperty("endDate") LocalDate endDate) {
        if (startDate == null) {
            throw new InvalidEntityException("startDate must be specified");
        }

        if (endDate == null) {
            throw new InvalidEntityException("endDate must be specified");
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidEntityException("startDate can't be after endDate");
        }

        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Start date of the date range, always non-null */
    public @Nonnull LocalDate getStartDate() {
        return startDate;
    }

    /** End date of the date range, always non-null. */
    public @Nonnull LocalDate getEndDate() {
        return endDate;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "DateRange{" + "startDate=" + startDate + ", endDate=" + endDate + '}';
    }
}
