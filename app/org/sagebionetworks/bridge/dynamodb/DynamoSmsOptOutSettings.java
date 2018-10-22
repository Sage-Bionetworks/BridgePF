package org.sagebionetworks.bridge.dynamodb;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;

import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

@DynamoThroughput(readCapacity=1, writeCapacity=1)
@DynamoDBTable(tableName = "SmsOptOut")
public class DynamoSmsOptOutSettings implements SmsOptOutSettings {
    private String phoneNumber;
    private boolean globalPromotionalOptOut;
    private Map<String, Boolean> promotionalOptOuts = new HashMap<>();
    private Map<String, Boolean> transactionalOptOuts = new HashMap<>();

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** {@inheritDoc} */
    @Override
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /** {@inheritDoc} */
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.BOOL)
    @Override
    public boolean getGlobalPromotionalOptOut() {
        return globalPromotionalOptOut;
    }

    /** {@inheritDoc} */
    @Override
    public void setGlobalPromotionalOptOut(boolean globalPromotionalOptOut) {
        this.globalPromotionalOptOut = globalPromotionalOptOut;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Boolean> getPromotionalOptOuts() {
        return promotionalOptOuts;
    }

    /** Sets promotional opt-out map. Used by Jackson and DynamoDB. */
    public void setPromotionalOptOuts(Map<String, Boolean> promotionalOptOuts) {
        this.promotionalOptOuts = new HashMap<>();
        if (promotionalOptOuts != null) {
            // This makes a copy and also ensures mutability.
            this.promotionalOptOuts.putAll(promotionalOptOuts);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Boolean> getTransactionalOptOuts() {
        return transactionalOptOuts;
    }

    /** Sets transactional opt-out map. Used by Jackson and DynamoDB. */
    public void setTransactionalOptOuts(Map<String, Boolean> transactionalOptOuts) {
        this.transactionalOptOuts = new HashMap<>();
        if (transactionalOptOuts != null) {
            // This makes a copy and also ensures mutability.
            this.transactionalOptOuts.putAll(transactionalOptOuts);
        }
    }
}
