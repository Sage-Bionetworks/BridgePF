package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.HealthCodeSalt;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "HealthCodeSalt")
public class DynamoHealthCodeSalt implements HealthCodeSalt, DynamoTable {

    private String id;
    private String salt;

    public DynamoHealthCodeSalt(String id, String salt) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be null or empty.");
        }
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("salt cannot be null or empty.");
        }
        this.id = id;
        this.salt = salt;
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
    public String getSalt() {
        return salt;
    }
    public void setSalt(String salt) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("salt cannot be null or empty.");
        }
        this.salt = salt;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((salt == null) ? 0 : salt.hashCode());
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
        DynamoHealthCodeSalt other = (DynamoHealthCodeSalt) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (salt == null) {
            if (other.salt != null) {
                return false;
            }
        } else if (!salt.equals(other.salt)) {
            return false;
        }
        return true;
    }
}
