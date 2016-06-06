package org.sagebionetworks.bridge.dao;

import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

public interface ReportDataDao {
    /**
     * Get report data records for the given date range. 
     *
     * @param key
     *         the key for this report
     * @param startDate
     *         start date for report
     * @param endDate
     *         end date for report
     * @return list of report data records in a resource list that includes original query values.
     */
    DateRangeResourceList<? extends ReportData> getReportData(ReportDataKey key, LocalDate startDate, LocalDate endDate);

    /**
     * Writes a report data record to the backing store. 
     *
     * @param reportData
     *         report data object
     */
    void saveReportData(ReportData reportData);
    
    /***
     * Delete all records regardless of date for a report. This can be used as part of testing, 
     * and in development, but there will be too many records in production to do a deletion.
     *  
     * @param key
     *      report to delete
     */
    void deleteReportData(ReportDataKey key);
}
