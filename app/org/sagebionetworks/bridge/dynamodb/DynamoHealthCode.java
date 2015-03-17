package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.commons.lang3.StringUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

/**
 * Used internally (i.e. no interface outside the dynamodb package) to
 * avoid collisions of health code. We do not trust that the underlying RNG
 * is always a solid one.
 */
@DynamoThroughput(readCapacity=50, writeCapacity=25)
@DynamoDBTable(tableName = "HealthCode")
public class DynamoHealthCode {

    private String code;
    private Long version;
    private String studyId;

    public DynamoHealthCode() {
    }

    public DynamoHealthCode(String code, String studyId) {
        checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty.");
        checkArgument(StringUtils.isNotBlank(studyId), "study identifier cannot be null or empty.");
        this.code = code;
        this.studyId = studyId;
    }

    @DynamoDBHashKey
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

    @DynamoDBAttribute
    public String getStudyIdentifier() {
        return studyId;
    }
    public void setStudyIdentifier(String studyId) {
        this.studyId = studyId;
    }
}
