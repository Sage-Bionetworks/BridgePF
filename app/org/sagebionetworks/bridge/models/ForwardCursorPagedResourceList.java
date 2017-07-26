package org.sagebionetworks.bridge.models;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A paged list of items from a data store that can provide a pointer to the next page of records, but 
 * which cannot return the total number of records across all pages (DynamoDB).
 */
public class ForwardCursorPagedResourceList<T> extends ResourceList<T> {

    private static final String HAS_NEXT = "hasNext";
    
    private final @Nullable String nextOffsetKey;

    @JsonCreator
    public ForwardCursorPagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(OFFSET_KEY) String nextOffsetKey) {
        super(items);
        this.nextOffsetKey = nextOffsetKey;
    }
    
    @Deprecated
    public DateTime getStartTime() {
        return getDateValue(START_TIME);
    }
    @Deprecated
    public DateTime getEndTime() {
        return getDateValue(END_TIME);
    }
    @Deprecated
    public DateTime getScheduledOnStart() {
        return getDateValue(SCHEDULED_ON_START);
    }
    @Deprecated
    public DateTime getScheduledOnEnd() {
        return getDateValue(SCHEDULED_ON_END);
    }
    @Deprecated
    public String getOffsetKey() {
        return nextOffsetKey;
    }
    @Deprecated
    public Integer getPageSize() {
        Object value = getRequestParams().get(PAGE_SIZE);
        return (value == null) ? null : (int)value;
    }
    public String getNextPageOffsetKey() {
        return nextOffsetKey;
    }
    @JsonProperty(HAS_NEXT)
    public boolean hasNext() {
        return (nextOffsetKey != null);
    }
    public ForwardCursorPagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
