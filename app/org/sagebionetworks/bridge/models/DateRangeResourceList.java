package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateRangeResourceList<T> extends ResourceList<T> {
    
    private final LocalDate startDate;
    private final LocalDate endDate;

    @JsonCreator
    public DateRangeResourceList(@JsonProperty(ITEMS_KEY) List<T> items, @JsonProperty(STARTDATE_KEY) LocalDate startDate,
            @JsonProperty(ENDDATE_KEY) LocalDate endDate) {
        super(items);
        this.startDate = startDate;
        this.endDate = endDate;
    }
    public LocalDate getStartDate() {
        return startDate;
    }
    public LocalDate getEndDate() {
        return endDate;
    }
    @Override
    public Map<String, Object> getRequestParams() {
        Map<String, Object> map = super.getRequestParams();
        addMapItem(map, STARTDATE_KEY, startDate);
        addMapItem(map, ENDDATE_KEY, endDate);
        return map;
    }
}
