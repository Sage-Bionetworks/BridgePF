package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface StudyCohort extends BridgeEntity {

    public void setStudyIdentifier(String studyIdentifier);
    public String getStudyIdentifier();
    
    public void setGuid(String guid);
    public String getGuid();
    
    public void setName(String name);
    public String getName();
    
    public void setDescription(String description);
    public String getDescription();
    
    public void setRequired(boolean required);
    public boolean isRequired();
    
    public void setDataGroup(String dataGroup);
    public String getDataGroup();
    
    public Integer getMinAppVersion();
    public void setMinAppVersion(Integer minAppVersion);
    
    public Integer getMaxAppVersion();
    public void setMaxAppVersion(Integer maxAppVersion);

    public Long getVersion();
    public void setVersion(Long version);

}
