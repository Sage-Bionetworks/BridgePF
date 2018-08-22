package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface SchedulePlanDao {

    List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, StudyIdentifier studyIdentifier, boolean includeDeleted);
    
    SchedulePlan getSchedulePlan(StudyIdentifier studyIdentifier, String guid);
    
    SchedulePlan createSchedulePlan(StudyIdentifier studyIdentifier, SchedulePlan plan);
    
    SchedulePlan updateSchedulePlan(StudyIdentifier studyIdentifier, SchedulePlan plan);
    
    void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid);
    
    void deleteSchedulePlanPermanently(StudyIdentifier studyIdentifier, String guid);
    
}
