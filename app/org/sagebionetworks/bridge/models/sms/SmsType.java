package org.sagebionetworks.bridge.models.sms;

/** Represents whether an SMS message is a transactional or a promotional message. */
public enum SmsType {
    /**
     * Promotional messages are anything other than transactional messages. This includes things like reminders,
     * notifications, and newsletters.
     */
    PROMOTIONAL("Promotional"),

    /**
     * Transactional messages are messages related to account workflow (sign-in, verification) or messages required by
     * governance (consent).
     */
    TRANSACTIONAL("Transactional");

    /** If you need to serialize this enum, this is the maximum length that will fit all of the values. */
    public static final int VALUE_MAX_LENGTH = 13;

    private final String value;

    SmsType(String value) {
        this.value = value;
    }

    /** The string value of the SMS type, used for things like AWS Simple Notification Service. */
    public String getValue() {
        return value;
    }
}
