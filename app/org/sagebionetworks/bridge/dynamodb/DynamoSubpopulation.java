package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.Subpopulation;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;

/**
 * A sub-population of the study participants who will receive a unique consent based on selection by a data group, 
 * an application version, or both. 
 */
@DynamoDBTable(tableName = "Subpopulation")
@BridgeTypeName("Subpopulation")
public final class DynamoSubpopulation implements Subpopulation {

    private String studyIdentifier;
    private String guid;
    private String name;
    private String description;
    private boolean required;
    private boolean deleted;
    private Integer minAppVersion;
    private Integer maxAppVersion;
    private Long version;
    private Set<String> allOfGroups;
    private Set<String> noneOfGroups;

    public DynamoSubpopulation() {
        this.allOfGroups = Sets.newHashSet();
        this.noneOfGroups = Sets.newHashSet();
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
    @DynamoDBRangeKey
    public String getGuid() {
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
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
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @DynamoDBAttribute
    @Override
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    @Override
    public void setAllOfGroups(Set<String> dataGroups) {
        this.allOfGroups = (dataGroups == null) ? Sets.newHashSet() : Sets.newHashSet(dataGroups);
    }
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @DynamoDBAttribute
    @Override
    public Set<String> getNoneOfGroups(){
        return noneOfGroups;
    }
    @Override
    public void setNoneOfGroups(Set<String> dataGroups){
        this.noneOfGroups = (dataGroups == null) ? Sets.newHashSet() : Sets.newHashSet(dataGroups);
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
    
    @Override
    public int hashCode() {
        return Objects.hash(allOfGroups, noneOfGroups, name, description, required, deleted, guid, 
                minAppVersion, maxAppVersion, studyIdentifier, version);
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
                && Objects.equals(studyIdentifier, other.studyIdentifier) && Objects.equals(version, other.version);
    }
    @Override
    public String toString() {
        return "DynamoSubpopulation [studyIdentifier=" + studyIdentifier + ", guid=" + guid + ", name=" + name
                + ", description=" + description + ", required=" + required + ", deleted=" + deleted 
                + ", allOfGroups=" + allOfGroups + ", noneOfGroups=" +  noneOfGroups + ", minAppVersion=" 
                + minAppVersion + ", maxAppVersion=" + maxAppVersion + ", version=" + version + "]";
    }

}
