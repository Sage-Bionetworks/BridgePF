package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@DynamoDBTable(tableName = "Criteria")
@BridgeTypeName("Criteria")
public final class DynamoCriteria implements Criteria {

    private String key;
    private String language;
    private Set<String> allOfGroups = Sets.newHashSet();
    private Set<String> noneOfGroups = Sets.newHashSet();
    private Set<String> allOfSubstudyIds = Sets.newHashSet();
    private Set<String> noneOfSubstudyIds = Sets.newHashSet();
    private Map<String, Integer> minAppVersions = Maps.newHashMap();
    private Map<String, Integer> maxAppVersions = Maps.newHashMap();
    
    @Override
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    @Override
    @DynamoDBAttribute    
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    
    /**
     * This property is supported for backwards compatibility with the existing table, but is no longer visible 
     * in the Criteria interface. The value is stored in the map of min app versions by platform, so this can be 
     * removed after migration.
     */
    @DynamoDBAttribute
    @JsonIgnore
    Integer getMinAppVersion() {
        return minAppVersions.get(IOS);
    }
    @JsonSetter
    void setMinAppVersion(Integer minAppVersion) {
        if (!minAppVersions.containsKey(IOS)) {
            setMinAppVersion(IOS, minAppVersion);
        }
    }
    /**
     * This property is supported for backwards compatibility with the existing table, but is no longer visible 
     * in the Criteria interface. The value is stored in the map of min app versions by platform, so this can be 
     * removed after migration.
     */
    @DynamoDBAttribute
    @JsonIgnore
    Integer getMaxAppVersion() {
        return maxAppVersions.get(IOS);
    }
    @JsonSetter
    void setMaxAppVersion(Integer maxAppVersion) {
        if (!maxAppVersions.containsKey(IOS)) {
            setMaxAppVersion(IOS, maxAppVersion);
        }
    }
    
    
    @Override
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    public void setAllOfGroups(Set<String> allOfGroups) {
        this.allOfGroups = (allOfGroups == null) ? new HashSet<>() : allOfGroups;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    public void setNoneOfGroups(Set<String> noneOfGroups) {
        this.noneOfGroups = (noneOfGroups == null) ? new HashSet<>() : noneOfGroups;
    }

    @Override
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getAllOfSubstudyIds() {
        return allOfSubstudyIds;
    }
    public void setAllOfSubstudyIds(Set<String> allOfSubstudyIds) {
        this.allOfSubstudyIds = (allOfSubstudyIds == null) ? new HashSet<>() : allOfSubstudyIds;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    public Set<String> getNoneOfSubstudyIds() {
        return noneOfSubstudyIds;
    }
    public void setNoneOfSubstudyIds(Set<String> noneOfSubstudyIds) {
        this.noneOfSubstudyIds = (noneOfSubstudyIds == null) ? new HashSet<>() : noneOfSubstudyIds;
    }
    
    /**
     * The map-based getter and setter supports DynamoDB persistence and the return of a JSON object/map in the API. In
     * the Java interface for Criteria, convenience methods to get/put values for an OS are exposed and the map is not
     * directly accessible. 
     */
    @DynamoDBAttribute
    @JsonGetter
    public Map<String, Integer> getMinAppVersions() {
        return ImmutableMap.copyOf(minAppVersions);
    }
    public void setMinAppVersions(Map<String, Integer> minAppVersions) {
        this.minAppVersions = (minAppVersions == null) ? new HashMap<>() :
                BridgeUtils.withoutNullEntries(minAppVersions);
    }
    @DynamoDBIgnore
    @Override
    public Integer getMinAppVersion(String osName) {
        return minAppVersions.get(osName);
    }
    @DynamoDBIgnore
    @Override
    public void setMinAppVersion(String osName, Integer minAppVersion) {
        BridgeUtils.putOrRemove(minAppVersions, osName, minAppVersion);
    }
    
    /**
     * The map-based getter and setter supports DynamoDB persistence and the return of a JSON object/map in the API. In
     * the Java interface for Criteria, convenience methods to get/put values for an OS are exposed and the map is not
     * directly accessible.
     */
    @DynamoDBAttribute
    @JsonGetter
    public Map<String, Integer> getMaxAppVersions() {
        return ImmutableMap.copyOf(maxAppVersions);
    }
    public void setMaxAppVersions(Map<String, Integer> maxAppVersions) {
        this.maxAppVersions = (maxAppVersions == null) ? new HashMap<>() :
                BridgeUtils.withoutNullEntries(maxAppVersions);
    }
    @DynamoDBIgnore
    @Override
    public Integer getMaxAppVersion(String osName) {
        return maxAppVersions.get(osName);
    }
    @DynamoDBIgnore
    @Override
    public void setMaxAppVersion(String osName, Integer maxAppVersion) {
        BridgeUtils.putOrRemove(maxAppVersions, osName, maxAppVersion);
    }
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public Set<String> getAppVersionOperatingSystems() {
        return new ImmutableSet.Builder<String>()
                .addAll(minAppVersions.keySet())
                .addAll(maxAppVersions.keySet()).build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, language, maxAppVersions, minAppVersions, allOfGroups, noneOfGroups, allOfSubstudyIds,
                noneOfSubstudyIds);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoCriteria other = (DynamoCriteria) obj;
        return Objects.equals(key,  other.key) && 
                Objects.equals(language, other.language) && 
                Objects.equals(noneOfGroups, other.noneOfGroups) && 
                Objects.equals(allOfGroups, other.allOfGroups) && 
                Objects.equals(noneOfSubstudyIds, other.noneOfSubstudyIds) && 
                Objects.equals(allOfSubstudyIds, other.allOfSubstudyIds) && 
                Objects.equals(minAppVersions, other.minAppVersions) && 
                Objects.equals(maxAppVersions, other.maxAppVersions);
    }
    @Override
    public String toString() {
        return "DynamoCriteria [key=" + key + ", language=" + language + ", allOfGroups=" + allOfGroups
                + ", noneOfGroups=" + noneOfGroups + ", minAppVersions=" + minAppVersions + ", maxAppVersions="
                + maxAppVersions + "]";
    }
}
