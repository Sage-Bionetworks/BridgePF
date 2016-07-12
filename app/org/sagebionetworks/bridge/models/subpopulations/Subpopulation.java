package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSubpopulation.class)
public interface Subpopulation extends BridgeEntity {

    public static Subpopulation create() {
        return new DynamoSubpopulation();
    }
    
    public void setStudyIdentifier(String studyIdentifier);
    public String getStudyIdentifier();

    public void setGuidString(String guid);
    public String getGuidString();

    public void setGuid(SubpopulationGuid guid);
    public SubpopulationGuid getGuid();
    
    public void setName(String name);
    public String getName();
    
    public void setDescription(String description);
    public String getDescription();
    
    /**
     * Is it required that the user sign the consent for this subpopulation in order
     * to access the Bridge server and participate in the study?
     * @param required
     */
    public void setRequired(boolean required);
    public boolean isRequired();
    
    /**
     * Has this subpopulation been deleted? The record remains for reconstructing historical 
     * consent histories, but it cannot be accessed through the APIs.
     * @param deleted
     */
    public void setDeleted(boolean deleted);
    public boolean isDeleted();
    
    /**
     * Is this subpopulation a default group? The first default subpopulation can be 
     * edited, but it cannot be deleted. Created for new studies or transitional 
     * studies.
     * @param defaultGroup
     */
    public void setDefaultGroup(boolean defaultGroup);
    public boolean isDefaultGroup();
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public void setCriteria(Criteria criteria);
    public Criteria getCriteria();
    
    public void setActiveConsentCreatedOn(long consentCreatedOn);
    public long getActiveConsentCreatedOn();
    
    /**
     * URL for retrieving the HTML version of the published consent for this study.
     * @return
     */
    public String getConsentHTML();
    
    /**
     * URL for retrieving the PDF version of the published consent for this study.
     * @return
     */
    public String getConsentPDF();

}
