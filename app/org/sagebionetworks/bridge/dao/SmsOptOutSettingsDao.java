package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

/** DAO for SMS opt-out settings objects. */
public interface SmsOptOutSettingsDao {
    /** Get the opt-out settings for the given phone number. */
    SmsOptOutSettings getOptOutSettings(String phoneNumber);

    /** Saves the given opt-out settings. */
    void setOptOutSettings(SmsOptOutSettings optOut);
}
