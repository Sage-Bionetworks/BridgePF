package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;

import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;

public class SmsMessageValidatorTest {
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;

    // branch coverage
    @Test
    public void supportsClass() {
        assertTrue(SmsMessageValidator.INSTANCE.supports(SmsMessage.class));
        assertFalse(SmsMessageValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SmsMessage");
        SmsMessageValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SmsMessage");
        SmsMessageValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validSmsMessage() {
        Validate.entityThrowingException(SmsMessageValidator.INSTANCE, makeValidSmsMessage());
    }

    @Test
    public void nullPhoneNumber() {
        SmsMessage message = makeValidSmsMessage();
        message.setPhoneNumber(null);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "number", "is required");
    }

    @Test
    public void emptyPhoneNumber() {
        SmsMessage message = makeValidSmsMessage();
        message.setPhoneNumber("");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "number", "is required");
    }

    @Test
    public void blankPhoneNumber() {
        SmsMessage message = makeValidSmsMessage();
        message.setPhoneNumber("   ");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "number", "is required");
    }

    @Test
    public void negativeSentOn() {
        SmsMessage message = makeValidSmsMessage();
        message.setSentOn(-1);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "sentOn", "must be positive");
    }

    @Test
    public void zeroSentOn() {
        SmsMessage message = makeValidSmsMessage();
        message.setSentOn(0);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "sentOn", "must be positive");
    }

    @Test
    public void nullMessageId() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageId(null);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageId", "is required");
    }

    @Test
    public void emptyMessageId() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageId("");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageId", "is required");
    }

    @Test
    public void blankMessageId() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageId("   ");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageId", "is required");
    }

    @Test
    public void nullMessageBody() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageBody(null);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageBody", "is required");
    }

    @Test
    public void emptyMessageBody() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageBody("");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageBody", "is required");
    }

    @Test
    public void blankMessageBody() {
        SmsMessage message = makeValidSmsMessage();
        message.setMessageBody("   ");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "messageBody", "is required");
    }

    @Test
    public void nullSmsType() {
        SmsMessage message = makeValidSmsMessage();
        message.setSmsType(null);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "smsType", "is required");
    }

    @Test
    public void nullStudyId() {
        SmsMessage message = makeValidSmsMessage();
        message.setStudyId(null);
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "studyId", "is required");
    }

    @Test
    public void emptyStudyId() {
        SmsMessage message = makeValidSmsMessage();
        message.setStudyId("");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "studyId", "is required");
    }

    @Test
    public void blankStudyId() {
        SmsMessage message = makeValidSmsMessage();
        message.setStudyId("   ");
        assertValidatorMessage(SmsMessageValidator.INSTANCE, message, "studyId", "is required");
    }

    private static SmsMessage makeValidSmsMessage() {
        SmsMessage message = SmsMessage.create();
        message.setPhoneNumber(PHONE_NUMBER);
        message.setSentOn(SENT_ON);
        message.setMessageId(MESSAGE_ID);
        message.setMessageBody(MESSAGE_BODY);
        message.setSmsType(SmsType.PROMOTIONAL);
        message.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        return message;
    }
}
