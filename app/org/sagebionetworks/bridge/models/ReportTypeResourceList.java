package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportTypeResourceList<T> extends ResourceList<T> {
    
    private static final String REPORT_TYPE = "reportType";
    
    private final ReportType reportType;

    @JsonCreator
    public ReportTypeResourceList(
            @JsonProperty(ITEMS) List<T> items,
            @JsonProperty(REPORT_TYPE) ReportType reportType) {
        super(items);
        this.reportType = reportType;
        if (reportType != null) {
            super.withRequestParam(REPORT_TYPE, reportType.name().toLowerCase());    
        }
    }
    public ReportType getReportType() {
        return reportType;
    }
}
