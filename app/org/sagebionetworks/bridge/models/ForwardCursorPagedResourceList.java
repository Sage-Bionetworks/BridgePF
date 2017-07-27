package org.sagebionetworks.bridge.models;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return getDateTime(START_TIME);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return getDateTime(END_TIME);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getScheduledOnStart() {
        return getDateTime(SCHEDULED_ON_START);
    }
    @Deprecated
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getScheduledOnEnd() {
        return getDateTime(SCHEDULED_ON_END);
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
