package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.HealthId;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoThroughput(writeCapacity=50, readCapacity=25)
@DynamoDBTable(tableName = "HealthId")
public class DynamoHealthId implements HealthId {

    private String id;
    private String code;
    private Long version;

    public DynamoHealthId() {}

    public DynamoHealthId(String id, String code) {
        checkArgument(StringUtils.isNotBlank(id), "id cannot be null or empty.");

        this.id = id;
        this.code = code;
    }

    @DynamoDBHashKey
    public String getId() {
        return id;
    }
    public void setId(String id) {
        checkArgument(StringUtils.isNotBlank(id), "id cannot be null or empty.");

        this.id = id;
    }

    @DynamoDBAttribute
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty.");

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
