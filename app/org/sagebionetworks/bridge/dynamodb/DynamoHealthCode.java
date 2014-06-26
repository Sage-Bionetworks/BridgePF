package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

/**
 * Used internally (i.e. no interface outside the dynamodb package) to
 * avoid collisions of health code. We do not trust that the underlying RNG
 * is always a solid one.
 */
@DynamoDBTable(tableName = "HealthCode")
public class DynamoHealthCode implements DynamoTable {

    private String code;
    private Long version;

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

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
