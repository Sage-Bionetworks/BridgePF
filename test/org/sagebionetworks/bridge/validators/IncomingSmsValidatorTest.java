package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;

import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.sms.IncomingSms;

public class IncomingSmsValidatorTest {
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final String PHONE_NUMBER = "+12065550123";

    // branch coverage
    @Test
    public void supportsClass() {
        assertTrue(IncomingSmsValidator.INSTANCE.supports(IncomingSms.class));
        assertFalse(IncomingSmsValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "IncomingSms");
        IncomingSmsValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "IncomingSms");
        IncomingSmsValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validIncomingSms() {
        Validate.entityThrowingException(IncomingSmsValidator.INSTANCE, makeValidIncomingSms());
    }

    @Test
    public void nullMessageId() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setMessageId(null);
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "messageId", "is required");
    }

    @Test
    public void emptyMessageId() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setMessageId("");
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "messageId", "is required");
    }

    @Test
    public void blankMessageId() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setMessageId("   ");
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "messageId", "is required");
    }

    @Test
    public void nullBody() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setBody(null);
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "body", "is required");
    }

    @Test
    public void emptyBody() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setBody("");
        Validate.entityThrowingException(IncomingSmsValidator.INSTANCE, incomingSms);
    }

    @Test
    public void blankBody() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setBody("   ");
        Validate.entityThrowingException(IncomingSmsValidator.INSTANCE, incomingSms);
    }

    @Test
    public void nullSenderNumber() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setSenderNumber(null);
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "senderNumber",
                "is required");
    }

    @Test
    public void emptySenderNumber() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setSenderNumber("");
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "senderNumber",
                "is required");
    }

    @Test
    public void blankSenderNumber() {
        IncomingSms incomingSms = makeValidIncomingSms();
        incomingSms.setSenderNumber("   ");
        assertValidatorMessage(IncomingSmsValidator.INSTANCE, incomingSms, "senderNumber",
                "is required");
    }

    private static IncomingSms makeValidIncomingSms() {
        IncomingSms incomingSms = new IncomingSms();
        incomingSms.setMessageId(MESSAGE_ID);
        incomingSms.setBody(MESSAGE_BODY);
        incomingSms.setSenderNumber(PHONE_NUMBER);
        return incomingSms;
    }
}
