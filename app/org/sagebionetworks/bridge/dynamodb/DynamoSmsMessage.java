package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;

@DynamoThroughput(readCapacity=1, writeCapacity=1)
@DynamoDBTable(tableName = "SmsMessage")
public class DynamoSmsMessage implements SmsMessage {
    private String phoneNumber;
    private long sentOn;
    private String healthCode;
    private String messageBody;
    private String messageId;
    private SmsType smsType;
    private String studyId;

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
    @DynamoDBIndexRangeKey(attributeName = "sentOn", globalSecondaryIndexName = "study-sentOn-index")
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getSentOn() {
        return sentOn;
    }

    /** {@inheritDoc} */
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    @Override
    public void setSentOn(long sentOn) {
        this.sentOn = sentOn;
    }

    /** {@inheritDoc} */
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** {@inheritDoc} */
    @Override
    public String getMessageBody() {
        return messageBody;
    }

    /** {@inheritDoc} */
    @Override
    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    /** {@inheritDoc} */
    @Override
    public String getMessageId() {
        return messageId;
    }

    /** {@inheritDoc} */
    @Override
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public SmsType getSmsType() {
        return smsType;
    }

    /** {@inheritDoc} */
    @Override
    public void setSmsType(SmsType smsType) {
        this.smsType = smsType;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "study-sentOn-index")
    @Override
    public String getStudyId() {
        return studyId;
    }

    /** {@inheritDoc} */
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
}
