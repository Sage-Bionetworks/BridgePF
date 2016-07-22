package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DateTimeRangeResourceList<T> {
    private final List<T> items;
    private final DateTime startTime;
    private final DateTime endTime;

    @JsonCreator
    public DateTimeRangeResourceList(@JsonProperty("items") List<T> items,
            @JsonProperty("startTime") DateTime startTime, @JsonProperty("endTime") DateTime endTime) {
        this.items = items;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    public List<T> getItems() {
        return items;
    }
    public int getTotal() {
        return items.size();
    }
    public DateTime getStartTime() {
        return startTime;
    }
    public DateTime getEndTime() {
        return endTime;
    }

}
