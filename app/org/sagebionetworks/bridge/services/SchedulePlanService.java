package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public interface SchedulePlanService {

    public List<SchedulePlan> getSchedulePlans(Study study);
    
    public SchedulePlan getSchedulePlan(Study study, String guid);
    
    public SchedulePlan createSchedulePlan(SchedulePlan plan);
    
    public SchedulePlan updateSchedulePlan(SchedulePlan plan);
    
    public void deleteSchedulePlan(Study study, String guid);
    
}
