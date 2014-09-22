package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This object represents the row in DynamoDB, but subclasses with parse the JSON data 
 * node for data specific to their strategy for creating schedules. They all also 
 * implement a functional generateSchedules() method.
 */
@DynamoDBTable(tableName = "SchedulePlan")
public class DynamoSchedulePlan implements SchedulePlan, DynamoTable {

    private static final String GUID_PROPERTY = "guid";
    private static final String STRATEGY_TYPE_PROPERTY = "strategyType";
    private static final String STUDY_KEY_PROPERTY = "studyKey";
    private static final String MODIFIED_ON_PROPERTY = "modifiedOn";
    private static final String DATA_PROPERTY = "data";
    
    private String guid;
    private String studyKey;
    private Long version;
    private long modifiedOn;
    private String strategyType;
    private ScheduleStrategy strategy;

    public static DynamoSchedulePlan fromJson(JsonNode node) {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(JsonUtils.asText(node, GUID_PROPERTY));
        plan.setModifiedOn(JsonUtils.asMillisSinceEpoch(node, MODIFIED_ON_PROPERTY));
        plan.setStudyKey(JsonUtils.asText(node, STUDY_KEY_PROPERTY));
        plan.setStrategyType(JsonUtils.asText(node, STRATEGY_TYPE_PROPERTY));
        plan.setData(JsonUtils.asObjectNode(node, DATA_PROPERTY));
        return plan;
    }
    
    @Override
    @DynamoDBHashKey
    public String getStudyKey() {
        return studyKey;
    }
    @Override
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
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
    @Override
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getModifiedOn() {
        return modifiedOn;
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    @Override
    @DynamoDBAttribute
    public String getStrategyType() {
        return strategyType;
    }
    @Override
    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }
    @Override
    @DynamoDBIgnore
    @JsonIgnore
    public ScheduleStrategy getScheduleStrategy() {
        return strategy;
    }
    @Override
    public void setScheduleStrategy(ScheduleStrategy strategy) {
        if (strategy != null) {
            this.strategyType = strategy.getClass().getSimpleName();    
        }
        this.strategy = strategy;
    }
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public ObjectNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        if (this.strategy != null) {
            this.strategy.persist(data);
        }
        return data;
    }
    public void setData(ObjectNode data) {
        this.strategy = JsonUtils.asScheduleStrategy(data, this.strategyType);
        this.strategy.initialize(data);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + (int) (modifiedOn ^ (modifiedOn >>> 32));
        result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
        result = prime * result + ((strategyType == null) ? 0 : strategyType.hashCode());
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
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
        DynamoSchedulePlan other = (DynamoSchedulePlan) obj;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (modifiedOn != other.modifiedOn)
            return false;
        if (strategy == null) {
            if (other.strategy != null)
                return false;
        } else if (!strategy.equals(other.strategy))
            return false;
        if (strategyType == null) {
            if (other.strategyType != null)
                return false;
        } else if (!strategyType.equals(other.strategyType))
            return false;
        if (studyKey == null) {
            if (other.studyKey != null)
                return false;
        } else if (!studyKey.equals(other.studyKey))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DynamoSchedulePlan [guid=" + guid + ", studyKey=" + studyKey + ", modifiedOn=" + modifiedOn
                + ", strategyType=" + strategyType + ", strategy=" + strategy + "]";
    }

}
