package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSubpopulation.class)
public interface Subpopulation extends BridgeEntity {

    static Subpopulation create() {
        return new DynamoSubpopulation();
    }
    
    void setStudyIdentifier(String studyIdentifier);
    String getStudyIdentifier();

    void setGuidString(String guid);
    String getGuidString();

    void setGuid(SubpopulationGuid guid);
    SubpopulationGuid getGuid();
    
    void setName(String name);
    String getName();
    
    void setDescription(String description);
    String getDescription();
    
    /**
     * Is it required that the user sign the consent for this subpopulation in order
     * to access the Bridge server and participate in the study?
     */
    void setRequired(boolean required);
    boolean isRequired();
    
    /**
     * Has this subpopulation been deleted? The record remains for reconstructing historical 
     * consent histories, but it cannot be accessed through the APIs.
     */
    void setDeleted(boolean deleted);
    boolean isDeleted();
    
    /**
     * Is this subpopulation a default group? The first default subpopulation can be 
     * edited, but it cannot be deleted. Created for new studies or transitional 
     * studies.
     */
    void setDefaultGroup(boolean defaultGroup);
    boolean isDefaultGroup();
    
    Long getVersion();
    void setVersion(Long version);
    
    void setCriteria(Criteria criteria);
    Criteria getCriteria();
    
    void setPublishedConsentCreatedOn(long consentCreatedOn);
    long getPublishedConsentCreatedOn();
    
    /**
     * URL for retrieving the HTML version of the published consent for this study.
     */
    String getConsentHTML();
    
    /**
     * URL for retrieving the PDF version of the published consent for this study.
     */
    String getConsentPDF();

}
