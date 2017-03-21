package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A paged list of items from a data store that can provide a pointer to the next page of records, but 
 * which cannot return the total number of records across all pages (DynamoDB).
 */
public class ForwardCursorPagedResourceList<T> {

    private final List<T> items;
    private final int pageSize;
    private final @Nullable String offsetBy;
    private final Map<String,String> filters = Maps.newHashMap();

    @JsonCreator
    public ForwardCursorPagedResourceList(
            @JsonProperty("items") List<T> items, 
            @JsonProperty("offsetBy") String offsetBy,
            @JsonProperty("pageSize") int pageSize) {
        this.items = items;
        this.offsetBy = offsetBy;
        this.pageSize = pageSize;
    }

    /**
     * A convenience method for adding filters without having to construct an intermediate map of filters.
     * e.g. PagedResourceList<T> page = new PagedResourceList<T>(....).withFilterValue("a","b");
     */
    public ForwardCursorPagedResourceList<T> withFilter(String key, String value) {
        if (isNotBlank(key) && isNotBlank(value)) {
            filters.put(key, value);
        }
        return this;
    }
    /**
     * A convenience method for adding a date filter.
     */
    public ForwardCursorPagedResourceList<T> withFilter(String key, DateTime value) {
        if (isNotBlank(key) && value != null) {
            filters.put(key, value.toString());
        }
        return this;
    }
    public List<T> getItems() {
        return items;
    }
    public String getOffsetBy() {
        return offsetBy;
    }
    @JsonProperty("hasNext")
    public boolean hasNext() {
        return (offsetBy != null);
    }
    public int getPageSize() {
        return pageSize;
    }
    @JsonAnyGetter
    public Map<String, String> getFilters() {
        return ImmutableMap.copyOf(filters);
    }
    @JsonAnySetter
    private void setFilter(String key, String value) {
        if (isNotBlank(key) && isNotBlank(value)) {
            filters.put(key, value);    
        }
    }
    @Override
    public String toString() {
        return "ForwardCursorPagedResourceList [items=" + items + ", offsetBy=" + offsetBy + ", pageSize=" + pageSize
                + ", filters=" + filters + "]";
    }

}
