package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.sms.SmsMessage;

/** DAO to keep a log of sent SMS messages and to retrieve the most recent SMS message. */
public interface SmsMessageDao {
    /** Retrieves the most recent SMS message that was sent to the given phone number. */
    SmsMessage getMostRecentMessage(String phoneNumber);

    /** Logs an SMS message. */
    void logMessage(SmsMessage smsMessage);
}
