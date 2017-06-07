package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;

/**
 * ForwardCursorPagedResourceList's offsetBy field was renamed to offsetKey. ScheduledActivity APIs were already in uses
 * by clients, which expect the offset to be named offsetBy. This class provides backwards compatibility.
 */
public class ScheduledActivityList extends ForwardCursorPagedResourceList<ScheduledActivity>{
    @JsonCreator
    public ScheduledActivityList(
            @JsonProperty("items") List<ScheduledActivity> items,
            @JsonProperty("offsetKey") String offsetKey,
            @JsonProperty("pageSize") int pageSize) {
        super(items, offsetKey, pageSize);
    }

    public String getOffsetBy() {
        return getOffsetKey();
    }
}
