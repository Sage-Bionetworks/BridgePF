package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface SchedulePlanDao {

    public List<SchedulePlan> getSchedulePlans(StudyIdentifier studyIdentifier);
    
    public SchedulePlan getSchedulePlan(StudyIdentifier studyIdentifier, String guid);
    
    public SchedulePlan createSchedulePlan(SchedulePlan plan);
    
    public SchedulePlan updateSchedulePlan(SchedulePlan plan);
    
    public void deleteSchedulePlan(StudyIdentifier studyIdentifier, String guid);
    
}
