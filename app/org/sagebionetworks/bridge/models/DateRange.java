package org.sagebionetworks.bridge.models;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.LocalDateDeserializer;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.LocalDateToStringSerializer;

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
    public DateRange(
            @Nonnull @JsonProperty("startDate") @JsonDeserialize(using = LocalDateDeserializer.class) LocalDate startDate,
            @Nonnull @JsonProperty("endDate") @JsonDeserialize(using = LocalDateDeserializer.class) LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("startDate must be specified");
        }

        if (endDate == null) {
            throw new IllegalArgumentException("endDate must be specified");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate can't be after endDate");
        }

        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Start date of the date range, always non-null */
    @JsonSerialize(using = LocalDateToStringSerializer.class)
    public @Nonnull LocalDate getStartDate() {
        return startDate;
    }

    /** End date of the date range, always non-null. */
    @JsonSerialize(using = LocalDateToStringSerializer.class)
    public @Nonnull LocalDate getEndDate() {
        return endDate;
    }
}
