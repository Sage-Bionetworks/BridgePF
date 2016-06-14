package org.sagebionetworks.bridge.dao;


import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ReportIndexDao {

    /**
     * Add an index item for a report, so the identifier of the report can be retrieved in a list of such identifiers.
     */
    void addIndex(ReportDataKey key);
    
    /**
     * Remove an index item for a report identifier. This can only be done for study reports, since we have no
     * performant way to determine if an identifier for a participant report is no longer in use by any participant. 
     * Still it is better than no cleanup.
     */
    void removeIndex(ReportDataKey key);
    
    /**
     * Get all the identifiers for a study, for either study or participant reports.
     */
    ReportTypeResourceList<? extends ReportIndex> getIndices(StudyIdentifier studyId, ReportType type);
    
}
