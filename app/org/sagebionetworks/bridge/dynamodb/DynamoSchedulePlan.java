package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This object represents the row in DynamoDB, but also converts the strategy JSON column in DynamoDB into a sub-class
 * of the ScheduleStrategy object, which implements specific algorithms for assigning users their schedules. 
 */
@DynamoDBTable(tableName = "SchedulePlan")
public final class DynamoSchedulePlan implements SchedulePlan {

    private static final String GUID_PROPERTY = "guid";
    private static final String LABEL_PROPERTY = "label";
    private static final String MODIFIED_ON_PROPERTY = "modifiedOn";
    private static final String STRATEGY_PROPERTY = "strategy";
    private static final String VERSION_PROPERTY = "version";
    private static final String DELETED_PROPERTY = "deleted";

    private String guid;
    private String label;
    private String studyKey;
    private boolean deleted;
    private Long version;
    private long modifiedOn;
    private ScheduleStrategy strategy;

    public static DynamoSchedulePlan fromJson(JsonNode node) {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(JsonUtils.asText(node, GUID_PROPERTY));
        plan.setLabel(JsonUtils.asText(node, LABEL_PROPERTY));
        plan.setModifiedOn(JsonUtils.asMillisSinceEpoch(node, MODIFIED_ON_PROPERTY));
        plan.setData(JsonUtils.asObjectNode(node, STRATEGY_PROPERTY));
        plan.setVersion(JsonUtils.asLong(node, VERSION_PROPERTY));
        plan.setDeleted(JsonUtils.asBoolean(node, DELETED_PROPERTY));
        return plan;
    }

    @Override
    @JsonIgnore
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
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getModifiedOn() {
        return modifiedOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
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
    public boolean isDeleted() {
        return deleted;
    }
    
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }


    @Override
    @DynamoDBIgnore
    public ScheduleStrategy getStrategy() {
        return strategy;
    }

    @Override
    public void setStrategy(ScheduleStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    @DynamoDBAttribute
    public String getLabel() {
        return label;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @DynamoDBAttribute(attributeName = "strategy")
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @JsonIgnore
    public ObjectNode getData() {
        ObjectNode node = BridgeObjectMapper.get().valueToTree(strategy);
        node.put("type", strategy.getClass().getSimpleName());
        return node;
    }

    public void setData(ObjectNode data) {
        if (data != null) {
            String typeName = JsonUtils.asText(data, "type");
            try {
                String className = BridgeConstants.SCHEDULE_STRATEGY_PACKAGE + typeName;
                Class<?> clazz = Class.forName(className);
                strategy = (ScheduleStrategy) BridgeObjectMapper.get().treeToValue(data, clazz);
            } catch (ClassCastException | ClassNotFoundException e) {
                throw new BadRequestException("Invalid type " + typeName);
            } catch (JsonProcessingException e) {
                throw new BridgeServiceException(e);
            }
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, label, modifiedOn, strategy, studyKey, version, deleted);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DynamoSchedulePlan other = (DynamoSchedulePlan) obj;
        return (Objects.equals(guid, other.guid) && Objects.equals(label, other.label)
                && Objects.equals(modifiedOn, other.modifiedOn) && Objects.equals(strategy, other.strategy)
                && Objects.equals(label, other.label) && Objects.equals(studyKey, other.studyKey)
                && Objects.equals(version, other.version) && Objects.equals(deleted, other.deleted));
    }

    @Override
    public String toString() {
        return String.format("DynamoSchedulePlan [guid=%s, label=%s, studyKey=%s, modifiedOn=%s, strategy=%s, deleted=%s]",
            guid, label, studyKey, modifiedOn, strategy, deleted);
    }

}
