package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * A sub-population of the study participants who will receive a unique consent based on selection by a data group, 
 * an application version, or both. 
 */
@DynamoDBTable(tableName = "Subpopulation")
@BridgeTypeName("Subpopulation")
public final class DynamoSubpopulation implements Subpopulation {

    private static final String DOCS_HOST = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");

    private String studyIdentifier;
    private String guid;
    private String name;
    private String description;
    private boolean required;
    private boolean deleted;
    private boolean defaultGroup;
    private Integer minAppVersion;
    private Integer maxAppVersion;
    private Long version;
    private Set<String> allOfGroups;
    private Set<String> noneOfGroups;
    private Criteria criteria;

    public DynamoSubpopulation() {
        this.allOfGroups = Sets.newHashSet();
        this.noneOfGroups = Sets.newHashSet();
    }
    
    @JsonIgnore
    @DynamoDBIgnore
    public String getKey() {
        return "subpopulation:"+guid;
    }
    
    @JsonIgnore
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
    @JsonIgnore
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
    @JsonIgnore
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
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @DynamoDBAttribute
    @Override
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    @Override
    public void setAllOfGroups(Set<String> dataGroups) {
        this.allOfGroups = (dataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(dataGroups);
    }
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @DynamoDBAttribute
    @Override
    public Set<String> getNoneOfGroups(){
        return noneOfGroups;
    }
    @Override
    public void setNoneOfGroups(Set<String> dataGroups){
        this.noneOfGroups = (dataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(dataGroups);
    }
    @DynamoDBAttribute
    @Override
    public Integer getMinAppVersion() {
        return minAppVersion;
    }
    @Override
    public void setMinAppVersion(Integer minAppVersion) {
        this.minAppVersion = minAppVersion;
    }
    @DynamoDBAttribute
    @Override
    public Integer getMaxAppVersion() {
        return maxAppVersion;
    }
    @Override
    public void setMaxAppVersion(Integer maxAppVersion) {
        this.maxAppVersion = maxAppVersion;
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
        this.criteria = criteria;
    }
    @DynamoDBIgnore
    @Override
    public Criteria getCriteria() {
        return criteria;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(allOfGroups, noneOfGroups, name, description, required, deleted, defaultGroup, guid,
                minAppVersion, maxAppVersion, studyIdentifier, version, criteria);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoSubpopulation other = (DynamoSubpopulation) obj;
        return Objects.equals(noneOfGroups, other.noneOfGroups)
                && Objects.equals(allOfGroups, other.allOfGroups) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && Objects.equals(guid, other.guid)
                && Objects.equals(minAppVersion, other.minAppVersion) && Objects.equals(required, other.required)
                && Objects.equals(maxAppVersion, other.maxAppVersion) && Objects.equals(deleted, other.deleted)
                && Objects.equals(studyIdentifier, other.studyIdentifier) && Objects.equals(version, other.version)
                && Objects.equals(defaultGroup, other.defaultGroup)
                && Objects.equals(criteria, other.criteria);
    }
    @Override
    public String toString() {
        return "DynamoSubpopulation [studyIdentifier=" + studyIdentifier + ", guid=" + guid + ", name=" + name
                + ", description=" + description + ", required=" + required + ", deleted=" + deleted + ", allOfGroups="
                + allOfGroups + ", noneOfGroups=" + noneOfGroups + ", minAppVersion=" + minAppVersion
                + ", maxAppVersion=" + maxAppVersion + ", criteria=" + criteria + ", version=" + version + "]";
    }

}
