package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.backfill.BackfillStatus;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "BackfillTask")
public class DynamoBackfillTask implements BackfillTask {

    private static final String SEPARATOR = ":";

    private String name;
    private long timestamp;
    private Long version;

    private String user;
    private String status;

    public DynamoBackfillTask() {
    }

    DynamoBackfillTask(String id) {
        String[] splits = id.split(SEPARATOR);
        checkArgument(splits.length == 2, "Invalid ID");
        this.name = splits[0];
        this.timestamp = Long.parseLong(splits[1]);
    }

    DynamoBackfillTask(String name, String user) {
        this.name = name;
        this.timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        this.user = user;
        this.status = BackfillStatus.SUBMITTED.name();
    }

    @DynamoDBHashKey
    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
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

    @Override
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDBIgnore
    @Override
    public String getId() {
        return name + SEPARATOR + timestamp;
    }
}
