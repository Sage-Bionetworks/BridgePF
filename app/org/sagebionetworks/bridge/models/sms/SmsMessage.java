package org.sagebionetworks.bridge.models.sms;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoSmsMessage;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Represents an SMS message that we sent to a phone number. This is used to log messages, and also to determine which
 * message the user is responding to when we receive an opt-out message.
 */
@BridgeTypeName("SmsMessage")
@JsonDeserialize(as = DynamoSmsMessage.class)
public interface SmsMessage extends BridgeEntity {
    /** Creates an SmsMessage instance. */
    static SmsMessage create() {
        return new DynamoSmsMessage();
    }

    /** The phone number we sent the message to. */
    String getNumber();

    /** @see #getNumber */
    void setNumber(String number);

    /** Timestamp in epoch milliseconds when we sent the message. */
    long getSentOn();

    /** @see #getSentOn */
    void setSentOn(long sentOn);

    /** The message content we sent. */
    String getMessageBody();

    /** @see #getMessageBody */
    void setMessageBody(String messageBody);

    /** Message ID, as determined by the SMS provider. */
    String getMessageId();

    /** @see #getMessageId */
    void setMessageId(String messageId);

    /** The type of message (promotional vs transactional). */
    SmsType getSmsType();

    /** @see #getSmsType */
    void setSmsType(SmsType smsType);

    /** The study whose behalf we sent the message. */
    String getStudyId();

    /** @see #getStudyId */
    void setStudyId(String studyId);
}
