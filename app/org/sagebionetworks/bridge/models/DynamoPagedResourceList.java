package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;

/**
 * Paging for a DynamoDB table, which uses a key to retrieve the next page of results (this is the only way 
 * you can move through pages of records, really just a cursor).
 */
@BridgeTypeName("PagedResourceList")
public final class DynamoPagedResourceList<T> {

    private final List<T> items;
    private final String lastKey;
    private final int pageSize;
    private final int total;
    private final Map<String,String> filters = Maps.newHashMap();
    
    @JsonCreator
    public DynamoPagedResourceList(@JsonProperty("items") List<T> items, @JsonProperty("lastKey") String lastKey,
            @JsonProperty("limitTo") int pageSize, @JsonProperty("total") int total,
            @JsonProperty("filters") Map<String, String> filters) {
        this.items = items;
        this.lastKey = lastKey;
        this.pageSize = pageSize;
        this.total = total;
        if (filters != null) {
            this.filters.putAll(filters);    
        }
    }

    public List<T> getItems() {
        return items;
    }

    public String getLastKey() {
        return lastKey;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotal() {
        return total;
    }

    @JsonAnyGetter
    public Map<String, String> getFilters() {
        return filters;
    }
    
    @JsonAnySetter
    public void setFilter(String key, String value) {
        if (isNotBlank(key) && isNotBlank(value)) {
            filters.put(key, value);    
        }
    }
    
    public void put(String filterKey, Object filterValue) {
        if (filterValue != null) {
            filters.put(filterKey, filterValue.toString());    
        }
    }

}
