package org.sagebionetworks.bridge.sms;

/** This controls whether we send SMS via AWS SNS or Twilio. */
public enum SmsServiceProvider {
    AWS,
    TWILIO,
}
