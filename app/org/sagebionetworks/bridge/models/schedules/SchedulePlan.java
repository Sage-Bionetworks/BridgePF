package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface SchedulePlan extends BridgeEntity {

    public String getGuid();
    public void setGuid(String guid);
    
    public String getStudyKey();
    public void setStudyKey(String studyKey);
    
    public long getModifiedOn();
    public void setModifiedOn(long modifiedOn);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getStrategyType();
    public void setStrategyType(String strategyType);
    
    public ScheduleStrategy getScheduleStrategy();
    public void setScheduleStrategy(ScheduleStrategy strategy);
    
}
