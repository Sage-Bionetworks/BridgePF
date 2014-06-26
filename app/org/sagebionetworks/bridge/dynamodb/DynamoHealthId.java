package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.HealthId;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "HealthId")
public class DynamoHealthId implements HealthId, DynamoTable {

    private String id;
    private String code;
    private Long version;

    public DynamoHealthId() {}

    public DynamoHealthId(String id, String code) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be null or empty.");
        }
        this.id = id;
        this.code = code;
    }

    @DynamoDBHashKey
    public String getId() {
        return id;
    }
    public void setId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be null or empty.");
        }
        this.id = id;
    }

    @DynamoDBAttribute
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code cannot be null or empty.");
        }
        this.code = code;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
