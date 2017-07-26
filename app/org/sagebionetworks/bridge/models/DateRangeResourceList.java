package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateRangeResourceList<T> extends ResourceList<T> {
    
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    
    private final LocalDate startDate;
    private final LocalDate endDate;

    @JsonCreator
    public DateRangeResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(START_DATE) LocalDate startDate,
            @JsonProperty(END_DATE) LocalDate endDate) {
        super(items);
        this.startDate = startDate;
        this.endDate = endDate;
        super.withRequestParam(START_DATE, startDate);
        super.withRequestParam(END_DATE, endDate);
    }
    
    @Deprecated
    public LocalDate getStartDate() {
        return startDate;
    }
    @Deprecated
    public LocalDate getEndDate() {
        return endDate;
    }
}
