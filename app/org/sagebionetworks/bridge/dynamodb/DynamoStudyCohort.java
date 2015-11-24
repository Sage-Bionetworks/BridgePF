package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.StudyCohort;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

/**
 * A sub-population of the study participants who will receive a unique consent based on select either by a data group
 * associated with the user, or the app version being submitted by the client (to make it possible to release the
 * application with a new consent).
 */
@DynamoDBTable(tableName = "StudyCohort")
@BridgeTypeName("StudyCohort")
public final class DynamoStudyCohort implements StudyCohort {

    private String studyIdentifier;
    private String guid;
    private String name;
    private String description;
    private boolean required;
    private String dataGroup;
    private Integer minAppVersion;
    private Integer maxAppVersion;
    private Long version;

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
    @Override
    public String getDataGroup() {
        return dataGroup;
    }
    @Override
    public void setDataGroup(String dataGroup) {
        this.dataGroup = dataGroup;
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
        return Objects.hash(dataGroup, name, description, required, guid, minAppVersion, maxAppVersion, studyIdentifier, version);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudyCohort other = (DynamoStudyCohort) obj;
        return Objects.equals(dataGroup, other.dataGroup) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && Objects.equals(guid, other.guid)
                && Objects.equals(minAppVersion, other.minAppVersion) && Objects.equals(required, other.required)
                && Objects.equals(maxAppVersion, other.maxAppVersion)
                && Objects.equals(studyIdentifier, other.studyIdentifier) 
                && Objects.equals(version, other.version);
    }
    @Override
    public String toString() {
        return "DynamoStudyCohort [studyIdentifier=" + studyIdentifier + ", guid=" + guid + ", name=" + name
                + ", description=" + description + ", required=" + required + ", dataGroup=" + dataGroup 
                + ", minAppVersion=" + minAppVersion + ", maxAppVersion=" + maxAppVersion + ", version=" + version + "]";
    }

}
