package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateRangeResourceList<T> extends ResourceList<T> {
    
    @JsonCreator
    public DateRangeResourceList(@JsonProperty(ITEMS) List<T> items) {
        super(items);
    }
    
    @Deprecated
    public LocalDate getStartDate() {
        return getLocalDate(START_DATE);
    }
    @Deprecated
    public LocalDate getEndDate() {
        return getLocalDate(END_DATE);
    }
    @Override
    @Deprecated
    public Integer getTotal() {
        // This is necessary solely to keep current integration tests passing. 
        // The total property is going away on everything except PagedResourceList.
        Integer total = super.getTotal();
        return (total == null) ? 0 : total;
    }
    public DateRangeResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
    
}
