package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Criteria;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(as=DynamoSubpopulation.class)
public interface Subpopulation extends BridgeEntity {
    ObjectWriter SUBPOP_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("studyIdentifier")));

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
     * When a consent is signed by a participant, we send the signed consent to them via email or 
     * via an SMS link. If this is true, suppress this behavior.
     */
    boolean isAutoSendConsentSuppressed();

    void setAutoSendConsentSuppressed(boolean autoSendConsentSuppressed);
    
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
     * Assign these data groups to the study participant when they consent to be a member 
     * of this subpopulation. If the user is withdrawn from this subpopulation, the same 
     * data groups will be removed, so they should probably be reserved for this flagging 
     * purpose only. 
     */
    void setDataGroupsAssignedWhileConsented(Set<String> dataGroups);
    Set<String> getDataGroupsAssignedWhileConsented();
    
    /**
     * Assign the participant to these substudies when they consent to be a member of this 
     * subpopulation. If the user is withdrawn from this subpopulation, the user will NOT 
     * be withdrawn from these substudies, which is an important difference from the 
     * assignment of data groups. 
     */
    void setSubstudyIdsAssignedOnConsent(Set<String> substudyIds);
    Set<String> getSubstudyIdsAssignedOnConsent();
    
    /**
     * URL for retrieving the HTML version of the published consent for this study.
     */
    String getConsentHTML();
    
    /**
     * URL for retrieving the PDF version of the published consent for this study.
     */
    String getConsentPDF();

}
