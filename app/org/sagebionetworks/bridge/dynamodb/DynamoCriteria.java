package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Preconditions;
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
     * This property is supported for backwards compatibility with the existing table, 
     * but is no longer visible, and the value is also stored in the map of min app versions
     * by platform. Can be removed in a future release.
     */
    @DynamoDBAttribute
    @JsonIgnore
    Integer getMinAppVersion() {
        return minAppVersions.get(IOS);
    }
    @JsonSetter
    void setMinAppVersion(Integer minAppVersion) {
        if (!minAppVersions.containsKey(IOS)) {
            minAppVersions.put(IOS, minAppVersion);    
        }
    }
    /**
     * This property is supported for backwards compatibility with the existing table, 
     * but is no longer visible, and the value is also stored in the map of max app versions
     * by platform. Can be removed in a future release
     */
    @DynamoDBAttribute
    @JsonIgnore
    Integer getMaxAppVersion() {
        return maxAppVersions.get(IOS);
    }
    @JsonSetter
    void setMaxAppVersion(Integer maxAppVersion) {
        if (!maxAppVersions.containsKey(IOS)) {
            maxAppVersions.put(IOS, maxAppVersion);    
        }
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    public void setAllOfGroups(Set<String> allOfGroups) {
        this.allOfGroups = (allOfGroups == null) ? Sets.newHashSet() : allOfGroups;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    public void setNoneOfGroups(Set<String> noneOfGroups) {
        this.noneOfGroups = (noneOfGroups == null) ? Sets.newHashSet() : noneOfGroups;
    }
    
    @DynamoDBAttribute
    @JsonGetter
    public Map<String, Integer> getMinAppVersions() {
        return ImmutableMap.copyOf(minAppVersions);
    }
    public void setMinAppVersions(Map<String, Integer> minAppVersions) {
        this.minAppVersions = (minAppVersions == null) ? new HashMap<>() : minAppVersions;
    }
    @DynamoDBIgnore
    @Override
    public Integer getMinAppVersion(String os) {
        return minAppVersions.get(os);
    }
    @DynamoDBIgnore
    @Override
    public void setMinAppVersion(String os, Integer minAppVersion) {
        checkArgument(isNotBlank(os));
        if (minAppVersion != null) {
            minAppVersions.put(os, minAppVersion);    
        } else {
            minAppVersions.remove(os);
        }
    }
    
    @DynamoDBAttribute
    @JsonGetter
    public Map<String, Integer> getMaxAppVersions() {
        return ImmutableMap.copyOf(maxAppVersions);
    }
    public void setMaxAppVersions(Map<String, Integer> maxAppVersions) {
        this.maxAppVersions = (maxAppVersions == null) ? new HashMap<>() : maxAppVersions;
    }
    @DynamoDBIgnore
    @Override
    public Integer getMaxAppVersion(String os) {
        return maxAppVersions.get(os);
    }
    @DynamoDBIgnore
    @Override
    public void setMaxAppVersion(String os, Integer maxAppVersion) {
        Preconditions.checkArgument(StringUtils.isNotBlank(os));
        if (maxAppVersion != null) {
            maxAppVersions.put(os, maxAppVersion);    
        } else {
            maxAppVersions.remove(os);
        }
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
        return Objects.hash(key, language, maxAppVersions, minAppVersions, allOfGroups, noneOfGroups);
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
