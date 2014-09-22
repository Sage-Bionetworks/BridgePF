package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public interface SchedulePlanDao {

    public List<? extends SchedulePlan> getSchedulePlans(Study study);
    
    public SchedulePlan getSchedulePlan(Study study, String guid);
    
    public GuidHolder createSchedulePlan(SchedulePlan plan);
    
    public void updateSchedulePlan(SchedulePlan plan);
    
    public void deleteSchedulePlan(Study study, String guid);
    
}
