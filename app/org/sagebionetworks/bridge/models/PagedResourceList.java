package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.DateTime;

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
    
    private final Integer total;

    // NOTE: Logically this could have a nextPageOffsetBy, but it's trivial to calculate client-side
    @JsonCreator
    public PagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(TOTAL) Integer total) {
        super(items);
        this.total = total;
    }

    @Deprecated
    public String getEmailFilter() {
        return (String)getRequestParams().get(EMAIL_FILTER);
    }
    @Deprecated
    public DateTime getStartTime() {
        return getDateTime(START_TIME);
    }
    @Deprecated
    public DateTime getEndTime() {
        return getDateTime(END_TIME);
    }
    @Deprecated
    public int getPageSize() {
        return (Integer)getRequestParams().get(PAGE_SIZE);
    }
    @Deprecated
    public Integer getOffsetBy() {
        return (Integer)getRequestParams().get(OFFSET_BY);
    }
    public Integer getTotal() {
        return total;
    }
    public PagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
