package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.studies.Study2;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@DynamoDBTable(tableName = "Study")
public class DynamoStudy implements Study2, DynamoTable {

    // We need the unconfigured mapper for setData/getData.
    private static ObjectMapper mapper = new ObjectMapper();
    private static TypeReference<HashMap<Environment,StudyEnvironment>> typeRef = new TypeReference<HashMap<Environment,StudyEnvironment>>() {};

    private static final String ENVIRONMENTS_PROPERTY = "environments";
    private static final String TRACKERS_PROPERTY = "trackers";
    private static final String MAXIMUM_NUMBER_OF_PARTICIPANTS_PROPERTY = "maxNumOfParticipants";
    private static final String MINIMUM_AGE_OF_CONSENT_PROPERTY = "minAgeOfConsent";
    private static final String RESEARCHER_ROLE_PROPERTY = "researcherRole";
    
    private String name;
    private String identifier;
    private String researcherRole;
    private int minAgeOfConsent;
    private int maxNumberOfParticipants;
    private List<String> trackers = Lists.newArrayList();
    private Map<Environment,StudyEnvironment> environments = Maps.newHashMap();
    private Long version;

    public static class StudyEnvironment {
        private String stormpathHref;
        public String getStormpathHref() {
            return stormpathHref;
        }
        public void setStormpathHref(String stormpathHref) {
            this.stormpathHref = stormpathHref;
        }
        @Override public String toString() {
            return "StudyEnvironments [stormpathHref=" + stormpathHref + "]";
        }
    };
    
    public DynamoStudy() {
        for (Environment env : Environment.values()) {
            environments.put(env, new StudyEnvironment());    
        }
    }
    
    @Override
    @DynamoDBAttribute
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    @DynamoDBHashKey
    public String getIdentifier() {
        return identifier;
    }
    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
    @Override
    @DynamoDBIgnore
    public String getStormpathUrl() {
        Environment env = BridgeConfigFactory.getConfig().getEnvironment();
        return environments.get(env).getStormpathHref();
    }
    @Override
    public void setStormpathUrl(Environment env, String value) {
        checkNotNull(env, Validate.CANNOT_BE_NULL, "environment");
        environments.get(env).setStormpathHref(value);
    }
    @DynamoDBIgnore
    @Override
    public String getResearcherRole() {
        return researcherRole;
    }
    @Override
    public void setResearcherRole(String role) {
        this.researcherRole = role;
    }
    @DynamoDBIgnore
    @Override
    public int getMinAgeOfConsent() {
        return minAgeOfConsent;
    }
    @Override
    public void setMinAgeOfConsent(int minAge) {
        this.minAgeOfConsent = minAge;
    }
    @DynamoDBIgnore
    @Override
    public int getMaxParticipants() {
        return maxNumberOfParticipants;
    }
    @Override
    public void setMaxParticipants(int maxParticipants) {
        this.maxNumberOfParticipants = maxParticipants;
    }
    @DynamoDBIgnore
    @Override
    public List<String> getTrackerIdentifiers() {
        return trackers;
    }
    @DynamoDBIgnore
    @JsonIgnore
    public Map<Environment,StudyEnvironment> getStudyEnvironments() {
        return environments;
    }
    
    @DynamoDBAttribute
    @JsonIgnore
    public String getData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(RESEARCHER_ROLE_PROPERTY, researcherRole);
        node.put(MINIMUM_AGE_OF_CONSENT_PROPERTY, minAgeOfConsent);
        node.put(MAXIMUM_NUMBER_OF_PARTICIPANTS_PROPERTY, maxNumberOfParticipants);
        node.set(TRACKERS_PROPERTY, mapper.valueToTree(trackers));
        node.set(ENVIRONMENTS_PROPERTY, mapper.valueToTree(environments));
        return node.toString();
    }
    public void setData(String data) {
        try {
            JsonNode node = mapper.readTree(data);
            this.researcherRole = JsonUtils.asText(node, RESEARCHER_ROLE_PROPERTY);
            this.minAgeOfConsent = JsonUtils.asIntPrimitive(node, MINIMUM_AGE_OF_CONSENT_PROPERTY);
            this.maxNumberOfParticipants = JsonUtils.asIntPrimitive(node, MAXIMUM_NUMBER_OF_PARTICIPANTS_PROPERTY);
            this.trackers = JsonUtils.asStringList(node, TRACKERS_PROPERTY);
            JsonNode depNode = JsonUtils.asJsonNode(node, ENVIRONMENTS_PROPERTY);
            this.environments = mapper.convertValue(depNode, typeRef);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((environments == null) ? 0 : environments.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + maxNumberOfParticipants;
        result = prime * result + minAgeOfConsent;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((researcherRole == null) ? 0 : researcherRole.hashCode());
        result = prime * result + ((trackers == null) ? 0 : trackers.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DynamoStudy other = (DynamoStudy) obj;
        if (environments == null) {
            if (other.environments != null)
                return false;
        } else if (!environments.equals(other.environments))
            return false;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        if (maxNumberOfParticipants != other.maxNumberOfParticipants)
            return false;
        if (minAgeOfConsent != other.minAgeOfConsent)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (researcherRole == null) {
            if (other.researcherRole != null)
                return false;
        } else if (!researcherRole.equals(other.researcherRole))
            return false;
        if (trackers == null) {
            if (other.trackers != null)
                return false;
        } else if (!trackers.equals(other.trackers))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DynamoStudy [name=" + name + ", identifier=" + identifier + ", researcherRole=" + researcherRole
                + ", minimumAgeOfConsent=" + minAgeOfConsent + ", maximumNumberOfParticipants="
                + maxNumberOfParticipants + ", trackers=" + trackers + ", environments=" + environments
                + ", version=" + version + "]";
    }
}
