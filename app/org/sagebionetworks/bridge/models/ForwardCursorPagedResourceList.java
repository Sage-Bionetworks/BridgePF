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

    private static final String SCHEDULED_ON_END = "scheduledOnEnd";
    private static final String SCHEDULED_ON_START = "scheduledOnStart";
    private static final String END_TIME = "endTime";
    private static final String START_TIME = "startTime";
    private static final String PAGE_SIZE = "pageSize";
    private static final String OFFSET_KEY = "offsetKey";
    
    private final int pageSize;
    private final @Nullable String offsetKey;

    @JsonCreator
    public ForwardCursorPagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(OFFSET_KEY) String offsetKey,
            @JsonProperty(PAGE_SIZE) int pageSize) {
        super(items);
        this.offsetKey = offsetKey;
        this.pageSize = pageSize;
        super.withRequestParam(OFFSET_KEY, offsetKey);
        super.withRequestParam(PAGE_SIZE, pageSize);
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
        return offsetKey;
    }
    public String getNextPageOffsetKey() {
        return offsetKey;
    }
    @JsonProperty("hasNext")
    public boolean hasNext() {
        return (offsetKey != null);
    }
    public int getPageSize() {
        return pageSize;
    }
    public ForwardCursorPagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
