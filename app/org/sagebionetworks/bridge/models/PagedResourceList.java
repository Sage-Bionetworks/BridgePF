package org.sagebionetworks.bridge.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PagedResourceList<T> {

    private final List<T> items;
    private final int offsetBy;
    private final int pageSize;
    private final int total;
    private final String filter;

    @JsonCreator
    public PagedResourceList(@JsonProperty("items") List<T> items, @JsonProperty("offsetBy") int offsetBy,
            @JsonProperty("limitTo") int pageSize, @JsonProperty("total") int total, 
            @JsonProperty("filterString") String filter) {
        this.items = items;
        this.offsetBy = offsetBy;
        this.pageSize = pageSize;
        this.total = total;
        this.filter = filter;
    }
    public List<T> getItems() {
        return items;
    }
    public int getTotal() {
        return total;
    }
    public int getOffsetBy() {
        return offsetBy;
    }
    public int getPageSize() {
        return pageSize;
    }
    public String getFilter() {
        return filter;
    }
    @Override
    public String toString() {
        return "PagedResourceList [items=" + items + ", offsetBy=" + offsetBy + ", pageSize=" + pageSize + ", total="
                + total + ", filter=" + filter + "]";
    }

}
