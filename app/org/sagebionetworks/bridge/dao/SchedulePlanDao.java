package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;

public interface SchedulePlanDao {

    public List<SchedulePlan> getSchedulePlans(Study study);
    
    public SchedulePlan getSchedulePlan(Study study, String guid);
    
    public SchedulePlan createSchedulePlan(SchedulePlan plan);
    
    public SchedulePlan updateSchedulePlan(SchedulePlan plan);
    
    public void deleteSchedulePlan(Study study, String guid);
    
}
