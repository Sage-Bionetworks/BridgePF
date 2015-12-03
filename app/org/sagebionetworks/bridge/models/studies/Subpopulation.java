package org.sagebionetworks.bridge.models.studies;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSubpopulation.class)
public interface Subpopulation extends Criteria {

    public static Subpopulation create() {
        return new DynamoSubpopulation();
    }
    
    public void setStudyIdentifier(String studyIdentifier);
    public String getStudyIdentifier();
    
    public void setGuid(String guid);
    public String getGuid();
    
    public void setName(String name);
    public String getName();
    
    public void setDescription(String description);
    public String getDescription();
    
    /**
     * Is this subpopulation required? Required subpopulations cannot be deleted. 
     * Right now the only required subpopulation is the default subpopulation, 
     * created for new studies or transitional studies.
     * @param required
     */
    public void setRequired(boolean required);
    public boolean isRequired();
    
    public void setDeleted(boolean deleted);
    public boolean isDeleted();
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public void setMinAppVersion(Integer minAppVersion);
    public void setMaxAppVersion(Integer minAppVersion);
    public void setAllOfGroups(Set<String> allOfGroups);
    public void setNoneOfGroups(Set<String> noneOfGroups);

}
