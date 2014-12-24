package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "BackfillRecord")
public class DynamoBackfillRecord implements BackfillRecord, DynamoTable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String taskId;
    private long timestamp;
    private Long version;
    
    private String studyId;
    private String accountId;
    private String operation;

    DynamoBackfillRecord(String taskId, String studyId, String accountId, String operation) {
        checkArgument(isNotBlank(taskId));
        checkArgument(isNotBlank(studyId));
        checkArgument(isNotBlank(accountId));
        checkArgument(isNotBlank(operation));
        this.taskId = taskId;
        this.timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        this.studyId = studyId;
        this.accountId = accountId;
        this.operation = operation;
    }

    @DynamoDBHashKey
    @Override
    public String getTaskId() {
        return taskId;
    }
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDBRangeKey
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getAccountId() {
        return accountId;
    }
    public void setAccountId(String account) {
        this.accountId = account;
    }

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    @DynamoDBIgnore
    public String getRecord() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("study", studyId);
        node.put("account", accountId);
        node.put("operation", operation);
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
