package org.sagebionetworks.bridge.sms;

import org.sagebionetworks.bridge.models.BridgeEntity;

/** An SMS message received via our webhook. */
public class IncomingSms implements BridgeEntity {
    private String messageId;
    private String body;
    private String senderPhoneNumber;

    /** Uniquely identifies this message. */
    public String getMessageId() {
        return messageId;
    }

    /** @see #getMessageId */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /** Message body. */
    public String getBody() {
        return body;
    }

    /** @see #getBody */
    public void setBody(String body) {
        this.body = body;
    }

    /** The phone number that sent this message. */
    public String getSenderPhoneNumber() {
        return senderPhoneNumber;
    }

    /** @see #getSenderPhoneNumber */
    public void setSenderPhoneNumber(String senderPhoneNumber) {
        this.senderPhoneNumber = senderPhoneNumber;
    }
}
