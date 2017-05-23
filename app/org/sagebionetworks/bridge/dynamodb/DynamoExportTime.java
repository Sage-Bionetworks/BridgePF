package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.models.ExportTime;

@DynamoDBTable(tableName = "ExportTime")
public class DynamoExportTime implements ExportTime{
    private String studyId;
    private Long lastExportDateTime;

    public DynamoExportTime() {
    }

    public DynamoExportTime(String studyId, Long lastExportDateTime) {
        checkArgument(StringUtils.isNotBlank(studyId), "code cannot be null or empty.");
        this.studyId = studyId;
        this.lastExportDateTime = lastExportDateTime;
    }

    @Override
    @DynamoDBHashKey
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        checkArgument(StringUtils.isNotBlank(studyId), "code cannot be null or empty.");
        this.studyId = studyId;
    }

    @Override
    @DynamoDBAttribute
    public Long getLastExportDateTime() {
        return lastExportDateTime;
    }

    @Override
    public void setLastExportDateTime(Long lastExportDateTime) {
        this.lastExportDateTime = lastExportDateTime;
    }
}
