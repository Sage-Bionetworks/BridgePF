package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

/**
 * Used internally (i.e. no interface outside the dynamodb package) to
 * avoid collisions of health code. We do not trust that the underlying RNG
 * is always a solid one.
 */
@DynamoDBTable(tableName = "HealthCode")
public class DynamoHealthCode implements DynamoTable {

    private String code;

    public DynamoHealthCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code cannot be null or empty.");
        }
        this.code = code;
    }

    @DynamoDBHashKey
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
        DynamoHealthCode other = (DynamoHealthCode) obj;
        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }
        return true;
    }
}
