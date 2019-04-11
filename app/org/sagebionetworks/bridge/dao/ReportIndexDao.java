package org.sagebionetworks.bridge.dao;


import java.util.Set;

import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ReportIndexDao {

    /**
     * Get a individual report index. This method is not exposed through the API and 
     * simply returns null if there is no index.
     * @return a report index, or null if there is no index
     */
    ReportIndex getIndex(ReportDataKey key);
    
    /**
     * Add an index item for a report, so the identifier of the report can be retrieved in a list of such identifiers.
     */
    void addIndex(ReportDataKey key, Set<String> substudies);
    
    /**
     * Update an existing index metadata.
     */
    void updateIndex(ReportIndex index);
    
    /**
     * Remove an index item for a report identifier. This is only done automatically for study reports, because we 
     * can't calculate if an identifier is still in use for a participant test in a performant way. But an endpoint 
     * is exposed so that admins can delete these index records as a part of test clean-up.
     */
    void removeIndex(ReportDataKey key);
    
    /**
     * Get all the identifiers for a study, for either study or participant reports.
     */
    ReportTypeResourceList<? extends ReportIndex> getIndices(StudyIdentifier studyId, ReportType type);
    
}
