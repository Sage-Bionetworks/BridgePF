package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DateTimeRangeResourceList<T> extends ResourceList<T> {
    private final DateTime startTime;
    private final DateTime endTime;

    @JsonCreator
    public DateTimeRangeResourceList(@JsonProperty(ITEMS_KEY) List<T> items,
            @JsonProperty(STARTTIME_KEY) DateTime startTime, @JsonProperty(ENDTIME_KEY) DateTime endTime) {
        super(items);
        this.startTime = startTime;
        this.endTime = endTime;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return startTime;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return endTime;
    }
    @Override
    public Map<String, Object> getRequestParams() {
        Map<String, Object> map = super.getRequestParams();
        addMapItem(map, STARTTIME_KEY, startTime);
        addMapItem(map, ENDTIME_KEY, endTime);
        return map;
    }
}
