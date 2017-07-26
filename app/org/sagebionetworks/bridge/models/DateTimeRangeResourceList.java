package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DateTimeRangeResourceList<T> extends ResourceList<T> {
    
    private final DateTime startTime;
    private final DateTime endTime;

    @JsonCreator
    public DateTimeRangeResourceList(
            @JsonProperty(ITEMS) List<T> items,
            @JsonProperty(START_TIME) DateTime startTime, 
            @JsonProperty(END_TIME) DateTime endTime) {
        super(items);
        this.startTime = startTime;
        this.endTime = endTime;
        super.withRequestParam(START_TIME, startTime);    
        super.withRequestParam(END_TIME, endTime);    
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return startTime;
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return endTime;
    }

}
