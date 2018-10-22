package org.sagebionetworks.bridge.sms;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.accounts.Phone;

@PrepareForTest({Message.class, Twilio.class})
@RunWith(PowerMockRunner.class)
public class TwilioHelperTest {
    private static final String ACCOUNT_SID = "my-account-sid";
    private static final String AUTH_TOKEN = "dummy-auth-token";
    private static final String MESSAGE_SID = "my-message-sid";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String RECIPIENT_NUMBER = "+12535556789";
    private static final String SENDER_NUMBER = "+12065550123";

    private TwilioHelper helper;

    @Before
    public void before() {
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.get(TwilioHelper.CONFIG_KEY_TWILIO_ACCOUNT_SID)).thenReturn(ACCOUNT_SID);
        when(mockConfig.get(TwilioHelper.CONFIG_KEY_TWILIO_AUTH_TOKEN)).thenReturn(AUTH_TOKEN);
        when(mockConfig.get(TwilioHelper.CONFIG_KEY_TWILIO_SENDER_NUMBER)).thenReturn(SENDER_NUMBER);

        helper = new TwilioHelper();

        // Need to mock static so we don't attempt to call the real Twilio.
        mockStatic(Twilio.class);
        helper.setBridgeConfig(mockConfig);
    }

    @Test
    public void initTwilio() {
        verifyStatic(Twilio.class);
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    @Test
    public void convertIncomingSms() {
        Map<String, String[]> formPostMap = ImmutableMap.<String, String[]>builder()
                .put(TwilioHelper.WEBHOOK_KEY_MESSAGE_SID, new String[] { MESSAGE_SID })
                .put(TwilioHelper.WEBHOOK_KEY_BODY, new String[] { MESSAGE_BODY })
                .put(TwilioHelper.WEBHOOK_KEY_FROM, new String[] { SENDER_NUMBER }).build();

        IncomingSms incomingSms = TwilioHelper.convertIncomingSms(formPostMap);
        assertEquals(MESSAGE_SID, incomingSms.getMessageId());
        assertEquals(MESSAGE_BODY, incomingSms.getBody());
        assertEquals(SENDER_NUMBER, incomingSms.getSenderPhoneNumber());
    }

    @Test
    public void send() {
        // Mock dependencies.
        Message mockMessage = mock(Message.class);
        when(mockMessage.getSid()).thenReturn(MESSAGE_SID);

        MessageCreator mockMessageCreator = mock(MessageCreator.class);
        when(mockMessageCreator.create()).thenReturn(mockMessage);

        mockStatic(Message.class);
        when(Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString())).thenReturn(
                mockMessageCreator);

        // Execute.
        String result = helper.sendSms(new Phone(RECIPIENT_NUMBER, "US"), MESSAGE_BODY);
        assertEquals(MESSAGE_SID, result);

        // Verify Twilio call.
        ArgumentCaptor<PhoneNumber> recipientNumberCaptor = ArgumentCaptor.forClass(PhoneNumber.class);
        ArgumentCaptor<PhoneNumber> senderNumberCaptor = ArgumentCaptor.forClass(PhoneNumber.class);
        verifyStatic(Message.class);
        Message.creator(recipientNumberCaptor.capture(), senderNumberCaptor.capture(), eq(MESSAGE_BODY));
        assertEquals(RECIPIENT_NUMBER, recipientNumberCaptor.getValue().getEndpoint());
        assertEquals(SENDER_NUMBER, senderNumberCaptor.getValue().getEndpoint());
    }
}
