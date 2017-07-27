package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resource lists that are paged and can report a total number of records across all 
 * pages.
 */
public class PagedResourceList<T> extends ResourceList<T> {

    private final int offsetBy;
    private final int pageSize;
    private final int total;

    @JsonCreator
    public PagedResourceList(
            @JsonProperty(ITEMS_KEY) List<T> items, 
            @JsonProperty(OFFSETBY_KEY) int offsetBy,
            @JsonProperty(PAGESIZE_KEY) int pageSize, 
            @JsonProperty(TOTAL_KEY) int total) {
        super(items);
        this.offsetBy = offsetBy;
        this.pageSize = pageSize;
        this.total = total;
    }
    public PagedResourceList<T> withFilter(String key, String value) {
        super.withFilter(key, value);
        return this;
    }
    public PagedResourceList<T> withFilter(String key, DateTime date) {
        super.withFilter(key, date);
        return this;
    }
    public int getOffsetBy() {
        return offsetBy;
    }
    public int getPageSize() {
        return pageSize;
    }
    public int getTotal() {
        return total;
    }
    @Override
    public Map<String, Object> getRequestParams() {
        Map<String, Object> map = super.getRequestParams();
        addMapItem(map, OFFSETBY_KEY, offsetBy);
        addMapItem(map, PAGESIZE_KEY, pageSize);
        return map;
    }
    @Override
    public String toString() {
        return "PagedResourceList [pageSize=" + pageSize + ", total=" + total + ", offsetBy=" + 
                offsetBy + ", filters=" + getFilters() + ", items=" + getItems() + "]";
    }
}
