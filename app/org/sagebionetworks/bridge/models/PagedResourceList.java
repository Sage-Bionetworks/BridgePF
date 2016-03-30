package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Due to this issue: https://github.com/FasterXML/jackson-databind/issues/921 you cannot deserialize a list 
 * with a generic type and also use a builder. Not fixed as of Jackson v2.7.3. We're using a pattern here 
 * that you see in the AWS SDK of having "withFoo" methods on an object that set and return the updated object 
 * (not a new object as everything is final here except the filter map, and that's only accessed as an 
 * ImmutableMap).
 */
public class PagedResourceList<T> {

    public static final String LAST_KEY_FILTER = "lastKey";
    
    private final List<T> items;
    /**
     * Calls from a RDMS use an offset index; DynamoDB uses an offsetKey which is usually a string, and 
     * added to the filer using the LAST_KEY_FILTER property. So offsetKey can be null when not in use.
     */
    private final @Nullable Integer offsetBy;
    private final int pageSize;
    private final int total;
    private final Map<String,String> filters = Maps.newHashMap();

    @JsonCreator
    public PagedResourceList(
            @JsonProperty("items") List<T> items, 
            @JsonProperty("offsetBy") Integer offsetBy,
            @JsonProperty("pageSize") int pageSize, 
            @JsonProperty("total") int total) {
        this.items = items;
        this.offsetBy = offsetBy;
        this.pageSize = pageSize;
        this.total = total;
    }

    /**
     * A convenience method for adding filters without having to construct an intermediate map of filters.
     * e.g. PagedResourceList<T> page = new PagedResourceList<T>(....).withFilterValue("a","b");
     */
    public PagedResourceList<T> withFilter(String key, String value) {
        if (isNotBlank(key) && isNotBlank(value)) {
            filters.put(key, value);
        }
        return this;
    }
    /**
     * Convenience method for adding the DDB key as a filter. The key must be returned to retrieve 
     * the next page of DDB records.
     */
    public PagedResourceList<T> withLastKey(String lastKey) {
        return withFilter(LAST_KEY_FILTER, lastKey);
    }
    public List<T> getItems() {
        return items;
    }
    public Integer getOffsetBy() {
        return offsetBy;
    }
    public String getLastKey() {
        return filters.get(LAST_KEY_FILTER);
    }
    public int getPageSize() {
        return pageSize;
    }
    public int getTotal() {
        return total;
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
        return "PagedResourceList [items=" + items + ", offsetBy=" + offsetBy + ", pageSize=" + pageSize + ", total="
                + total + ", filters=" + filters + "]";
    }
}
