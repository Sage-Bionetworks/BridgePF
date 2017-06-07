package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;

/**
 * ForwardCursorPagedResourceList's offsetBy field was renamed to offsetKey. ScheduledActivity APIs were already in uses
 * by clients, which expect the offset to be named offsetBy. This class provides backwards compatibility.
 */
public class ScheduledActivityList extends ForwardCursorPagedResourceList<ScheduledActivity>{
    public ScheduledActivityList(
            List<ScheduledActivity> items, String offsetBy, int pageSize) {
        super(items, offsetBy, pageSize);
    }

    public String getOffsetBy() {
        return getOffsetKey();
    }
}
