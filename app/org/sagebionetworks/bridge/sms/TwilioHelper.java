package org.sagebionetworks.bridge.sms;

import java.util.Map;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.accounts.Phone;

/**
 * This class encapsulates Twilio specific implementations, like webhook input parsing and response XML and APIs for
 * sending SMS messages.
 */
@Component
public class TwilioHelper {
    static final String CONFIG_KEY_TWILIO_ACCOUNT_SID = "twilio.account.sid";
    static final String CONFIG_KEY_TWILIO_AUTH_TOKEN = "twilio.auth.token";
    static final String CONFIG_KEY_TWILIO_SENDER_NUMBER = "twilio.sender.number";

    public static final String WEBHOOK_KEY_MESSAGE_SID = "MessageSid";
    public static final String WEBHOOK_KEY_BODY = "Body";
    public static final String WEBHOOK_KEY_FROM = "From";

    // Response XML templates for Twilio Webhooks
    public static final String RESPONSE_MESSAGE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Say>%s" +
            "</Say></Response>";
    public static final String RESPONSE_NOOP = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>";

    private PhoneNumber senderPhoneNumber;

    /** Configure Twilio using Bridge config. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        // Twilio must be statically initialized, but Spring doesn't support static initializers. Note, do not ever
        // try to instantiate more than one TwilioHelper with different credentials. It will almost certainly not work
        // the way you expect.
        String accountSid = bridgeConfig.get(CONFIG_KEY_TWILIO_ACCOUNT_SID);
        String authToken = bridgeConfig.get(CONFIG_KEY_TWILIO_AUTH_TOKEN);
        Twilio.init(accountSid, authToken);

        senderPhoneNumber = new PhoneNumber(bridgeConfig.get(CONFIG_KEY_TWILIO_SENDER_NUMBER));
    }

    /** Helper method to parse an incoming SMS sent from Twilio to our webhook. */
    public static IncomingSms convertIncomingSms(Map<String, String[]> formPostMap) {
        IncomingSms incomingSms = new IncomingSms();
        incomingSms.setMessageId(formPostMap.get(WEBHOOK_KEY_MESSAGE_SID)[0]);
        incomingSms.setBody(formPostMap.get(WEBHOOK_KEY_BODY)[0]);
        incomingSms.setSenderPhoneNumber(formPostMap.get(WEBHOOK_KEY_FROM)[0]);
        return incomingSms;
    }

    /** Sends the given SMS message to the given phone number. */
    public String sendSms(Phone recipientNumber, String message) {
        Message twilioMessage = Message.creator(new PhoneNumber(recipientNumber.getNumber()), senderPhoneNumber,
                message).create();
        return twilioMessage.getSid();
    }
}
