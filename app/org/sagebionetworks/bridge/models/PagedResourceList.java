package org.sagebionetworks.bridge.models;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This list represents one page of a larger list, for which we know the total number of items in the list 
 * (not just the number in the page). Request parameters are specifically modeled for this form of paging 
 * (pageSize, offsetBy, and total).
 *  
 * Due to this issue: https://github.com/FasterXML/jackson-databind/issues/921 you cannot deserialize a list 
 * with a generic type and also use a builder. Not fixed as of Jackson v2.7.3. We're using a pattern here 
 * that you see in the AWS SDK of having "withFoo" methods on an object that set and return the updated object 
 * (not a new object as everything is final here except the filter map, and that's only accessed as an 
 * ImmutableMap).
 */
public class PagedResourceList<T> extends ResourceList<T> {

    private static final String PAGE_SIZE = "pageSize";
    private static final String OFFSET_BY = "offsetBy";
    private static final String TOTAL = "total";
    
    /**
     * Calls from a RDMS use an offset index; DynamoDB uses an offsetKey which is usually a string, and 
     * added to the filer using the LAST_KEY_FILTER property. So offsetKey can be null when not in use.
     */
    private final @Nullable Integer offsetBy;
    private final int pageSize;
    private final int total;

    @JsonCreator
    public PagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(OFFSET_BY) Integer offsetBy,
            @JsonProperty(PAGE_SIZE) int pageSize, 
            @JsonProperty(TOTAL) int total) {
        super(items);
        this.offsetBy = offsetBy;
        this.pageSize = pageSize;
        this.total = total;
        super.withRequestParam(OFFSET_BY, offsetBy);
        super.withRequestParam(PAGE_SIZE, pageSize);
        super.withRequestParam(TOTAL, total);
    }

    public Integer getOffsetBy() {
        return offsetBy;
    }
    public int getPageSize() {
        return pageSize;
    }
    public int getTotal() {
        return total;
    }
    public PagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
