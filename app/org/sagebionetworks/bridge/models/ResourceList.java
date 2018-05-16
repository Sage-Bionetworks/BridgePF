package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Basic list of items, not paged, as calculated based on the parameters that were 
 * sent to the server and are included in the <code>ResourceList</code>.
 */
public class ResourceList<T> {
    
    public static final String ALL_OF_GROUPS = "allOfGroups";
    public static final String ASSIGNMENT_FILTER = "assignmentFilter";
    public static final String EMAIL_FILTER = "emailFilter";
    public static final String END_DATE = "endDate";
    public static final String END_TIME = "endTime";
    public static final String ID_FILTER = "idFilter";
    public static final String LANGUAGE = "language";
    public static final String NEXT_PAGE_OFFSET_KEY = "nextPageOffsetKey";
    public static final String NONE_OF_GROUPS = "noneOfGroups";
    public static final String OFFSET_BY = "offsetBy";
    public static final String OFFSET_KEY = "offsetKey";
    public static final String PAGE_SIZE = "pageSize";
    public static final String PHONE_FILTER = "phoneFilter";
    public static final String REPORT_TYPE = "reportType";
    public static final String SCHEDULED_ON_END = "scheduledOnEnd";
    public static final String SCHEDULED_ON_START = "scheduledOnStart";
    public static final String START_DATE = "startDate";
    public static final String START_TIME = "startTime";
    public static final String TOTAL = "total";
    public static final String TYPE = "type";
    public static final String REQUEST_PARAMS = "RequestParams";
    
    protected static final String ITEMS = "items";
    
    private final List<T> items;
    private final Map<String,Object> requestParams = new HashMap<>();

    @JsonCreator
    public ResourceList(@JsonProperty(ITEMS) List<T> items) {
        checkNotNull(items);
        this.items = items;
        this.requestParams.put(TYPE, REQUEST_PARAMS);
    }
    public List<T> getItems() {
        return items;
    }
    public Map<String, Object> getRequestParams() {
        return ImmutableMap.copyOf(requestParams);
    }
    public ResourceList<T> withRequestParam(String key, Object value) {
        if (!ResourceList.TYPE.equals(key) && isNotBlank(key) && value != null) {
            if (value instanceof DateTime) {
                // For DateTime, forcing toString() here rather than using Jackson's serialization mechanism, 
                // ensures the string is in the timezone supplied by the user.
                requestParams.put(key, value.toString());    
            } else {
                requestParams.put(key, value);    
            }
        }
        return this;
    }
    @Deprecated
    public Integer getTotal() {
        return (items.isEmpty()) ? null : items.size();
    }
    protected DateTime getDateTime(String fieldName) {
        String value = (String)requestParams.get(fieldName);
        return (value == null) ? null : DateTime.parse(value);
    }
    protected LocalDate getLocalDate(String fieldName) {
        Object object = requestParams.get(fieldName);
        if (object instanceof String) {
            return LocalDate.parse((String)object);
        }
        return (LocalDate)object;
    }
}