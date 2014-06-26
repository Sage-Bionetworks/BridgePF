package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.HealthId;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "HealthId")
public class DynamoHealthId implements HealthId, DynamoTable {

    private String id;
    private String code;

    public DynamoHealthId(String id, String code) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be null or empty.");
        }
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code cannot be null or empty.");
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DynamoHealthId other = (DynamoHealthId) obj;
        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
}
