package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ExportTime;

public interface ExportTimeDao {

    ExportTime getExportTime(String studyId);
    void deleteStudyInfo(String studyId);
}
