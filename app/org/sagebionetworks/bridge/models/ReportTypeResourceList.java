package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportTypeResourceList<T> extends ResourceList<T> {
    
    @JsonCreator
    public ReportTypeResourceList(@JsonProperty(ITEMS) List<T> items) {
        super(items);
    }
    @Deprecated
    public ReportType getReportType() {
        return (ReportType)getRequestParams().get(REPORT_TYPE);
    }
    public ReportTypeResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
    
}
