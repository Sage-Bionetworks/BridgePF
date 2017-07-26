package org.sagebionetworks.bridge.models;

import java.util.List;

import javax.annotation.Nullable;

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
    
    private final @Nullable Integer offsetBy;
    private final Integer total;

    @JsonCreator
    public PagedResourceList(
            @JsonProperty(ITEMS) List<T> items, 
            @JsonProperty(OFFSET_BY) Integer offsetBy,
            @JsonProperty(TOTAL) int total) {
        super(items);
        this.offsetBy = offsetBy;
        this.total = total;
        super.withRequestParam(OFFSET_BY, offsetBy);
        super.withRequestParam(TOTAL, total);
    }

    @Deprecated
    public String getEmailFilter() {
        return (String)getRequestParams().get(EMAIL_FILTER);
    }
    @Deprecated
    public DateTime getStartDate() {
        return getDateValue(START_DATE);
    }
    @Deprecated
    public DateTime getEndDate() {
        return getDateValue(END_DATE);
    }
    @Deprecated
    public int getPageSize() {
        return (Integer)getRequestParams().get(PAGE_SIZE);
    }
    public Integer getOffsetBy() {
        return offsetBy;
    }
    public Integer getTotal() {
        return total;
    }
    public PagedResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
}
