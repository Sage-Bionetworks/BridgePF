package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("SchedulePlan")
public interface SchedulePlan extends BridgeEntity {

    public String getGuid();
    public void setGuid(String guid);
    
    public String getLabel();
    public void setLabel(String label);
    
    public String getStudyKey();
    public void setStudyKey(String studyKey);
    
    public long getModifiedOn();
    public void setModifiedOn(long modifiedOn);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public ScheduleStrategy getStrategy();
    public void setStrategy(ScheduleStrategy strategy);
    
    public Integer getMinAppVersion();
    public void setMinAppVersion(Integer minAppVersion);
    
    public Integer getMaxAppVersion();
    public void setMaxAppVersion(Integer maxAppVersion);
}
