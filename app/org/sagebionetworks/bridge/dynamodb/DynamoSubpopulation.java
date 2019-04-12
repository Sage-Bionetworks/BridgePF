package org.sagebionetworks.bridge.dynamodb;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A sub-population of the study participants who will receive a unique consent based on selection by a data group, 
 * an application version, or both. 
 */
@DynamoDBTable(tableName = "Subpopulation")
@BridgeTypeName("Subpopulation")
@JsonFilter("filter")
public final class DynamoSubpopulation implements Subpopulation {

    private static final String DOCS_HOST = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");

    private String studyIdentifier;
    private String guid;
    private String name;
    private String description;
    private boolean required;
    private boolean deleted;
    private boolean defaultGroup;
    private boolean autoSendConsentSuppressed;
    private Long version;
    private long publishedConsentCreatedOn;
    private Criteria criteria;
    private Set<String> dataGroupsAssignedWhileConsented;
    private Set<String> substudyIdsAssignedOnConsent;

    public DynamoSubpopulation() {
        criteria = Criteria.create();
        dataGroupsAssignedWhileConsented = new HashSet<>();
        substudyIdsAssignedOnConsent = new HashSet<>();
    }
    
    @Override
    @DynamoDBHashKey
    public String getStudyIdentifier() {
        return studyIdentifier;
    }
    @Override
    public void setStudyIdentifier(String studyIdentifier) {
        this.studyIdentifier = studyIdentifier;
    }
    @Override
    @DynamoDBRangeKey(attributeName="guid")
    @JsonProperty("guid")
    public String getGuidString() {
        return guid;
    }
    @Override
    public void setGuidString(String guid) {
        this.guid = guid;
    }
    @Override
    @DynamoDBIgnore
    public SubpopulationGuid getGuid() {
        return (guid == null) ? null : SubpopulationGuid.create(guid);
    }    
    @Override
    public void setGuid(SubpopulationGuid subpopGuid) {
        this.guid = (subpopGuid == null) ? null : subpopGuid.getGuid();
    }
    @DynamoDBAttribute
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @DynamoDBAttribute
    @Override
    public String getDescription() {
        return description;
    }
    @Override
    public void setDescription(String description) {
        this.description = description;
    }
    @DynamoDBAttribute
    @Override
    public boolean isRequired() {
        return required;
    }
    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }
    @DynamoDBAttribute
    @Override
    public boolean isDeleted() {
        return deleted;
    }
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @DynamoDBAttribute
    @Override
    public boolean isDefaultGroup() {
        return defaultGroup;
    }
    @Override
    public void setDefaultGroup(boolean defaultGroup) {
        this.defaultGroup = defaultGroup;
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getPublishedConsentCreatedOn() {
        return publishedConsentCreatedOn;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setPublishedConsentCreatedOn(long consentCreatedOn) {
        this.publishedConsentCreatedOn = consentCreatedOn;
    }
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    /** {@inheritDoc} */
    @Override
    @DynamoDBIgnore
    public String getConsentHTML() {
        return String.format("http://%s/%s/consent.html", DOCS_HOST, guid);
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBIgnore
    public String getConsentPDF() {
        return String.format("http://%s/%s/consent.pdf", DOCS_HOST, guid);
    }
    @Override
    public void setCriteria(Criteria criteria) {
        this.criteria = (criteria != null) ? criteria : Criteria.create();
    }
    @DynamoDBIgnore
    @Override
    public Criteria getCriteria() {
        return criteria;
    }
    @DynamoDBAttribute
    @Override
    public boolean isAutoSendConsentSuppressed() {
        return autoSendConsentSuppressed;
    }
    @Override
    public void setAutoSendConsentSuppressed(boolean autoSendConsentSuppressed) {
        this.autoSendConsentSuppressed = autoSendConsentSuppressed;
    }
    
    /** {@inheritDoc */
    public void setDataGroupsAssignedWhileConsented(Set<String> dataGroups) {
        this.dataGroupsAssignedWhileConsented = (dataGroups != null) ? 
                new HashSet<>(dataGroups) : new HashSet<>();
    }
    
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getDataGroupsAssignedWhileConsented(){
        return dataGroupsAssignedWhileConsented;
    }

    /** {@inheritDoc */
    public void setSubstudyIdsAssignedOnConsent(Set<String> substudyIds) {
        this.substudyIdsAssignedOnConsent = (substudyIds != null) ? 
                new HashSet<>(substudyIds) : new HashSet<>();
    }
    
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getSubstudyIdsAssignedOnConsent() {
        return substudyIdsAssignedOnConsent;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, description, required, deleted, defaultGroup, guid, studyIdentifier,
                publishedConsentCreatedOn, version, criteria, autoSendConsentSuppressed, dataGroupsAssignedWhileConsented,
                substudyIdsAssignedOnConsent);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoSubpopulation other = (DynamoSubpopulation) obj;
        return Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && Objects.equals(guid, other.guid) && Objects.equals(required, other.required)
                && Objects.equals(deleted, other.deleted) && Objects.equals(studyIdentifier, other.studyIdentifier)
                && Objects.equals(publishedConsentCreatedOn, other.publishedConsentCreatedOn)
                && Objects.equals(version, other.version) && Objects.equals(defaultGroup, other.defaultGroup)
                && Objects.equals(criteria, other.criteria)
                && Objects.equals(autoSendConsentSuppressed, other.autoSendConsentSuppressed)
                && Objects.equals(dataGroupsAssignedWhileConsented, other.dataGroupsAssignedWhileConsented)
                && Objects.equals(substudyIdsAssignedOnConsent, other.substudyIdsAssignedOnConsent);
    }
    @Override
    public String toString() {
        return "DynamoSubpopulation [studyIdentifier=" + studyIdentifier + ", guid=" + guid + ", name=" + name
                + ", description=" + description + ", required=" + required + ", deleted=" + deleted + ", criteria="
                + criteria + ", publishedConsentCreatedOn=" + publishedConsentCreatedOn + ", version=" + version
                + ", autoSendConsentSuppressed=" + autoSendConsentSuppressed + ", dataGroupsAssignedWhileConsented="
                + dataGroupsAssignedWhileConsented + ", substudyIdsAssignedOnConsent=" + substudyIdsAssignedOnConsent + "]";
    }

}
