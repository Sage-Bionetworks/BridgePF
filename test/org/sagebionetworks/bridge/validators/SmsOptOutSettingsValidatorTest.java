package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;

import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

public class SmsOptOutSettingsValidatorTest {
    private static final String PHONE_NUMBER = "+12065550123";

    // branch coverage
    @Test
    public void supportsClass() {
        assertTrue(SmsOptOutSettingsValidator.INSTANCE.supports(SmsOptOutSettings.class));
        assertFalse(SmsOptOutSettingsValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SmsOptOutSettings");
        SmsOptOutSettingsValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // We call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types.
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SmsOptOutSettings");
        SmsOptOutSettingsValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validOptOutSettings() {
        Validate.entityThrowingException(SmsOptOutSettingsValidator.INSTANCE, makeValidOptOutSettings());
    }

    @Test
    public void nullPhoneNumber() {
        SmsOptOutSettings settings = makeValidOptOutSettings();
        settings.setPhoneNumber(null);
        assertValidatorMessage(SmsOptOutSettingsValidator.INSTANCE, settings, "number", "is required");
    }

    @Test
    public void emptyPhoneNumber() {
        SmsOptOutSettings settings = makeValidOptOutSettings();
        settings.setPhoneNumber("");
        assertValidatorMessage(SmsOptOutSettingsValidator.INSTANCE, settings, "number", "is required");
    }

    @Test
    public void blankPhoneNumber() {
        SmsOptOutSettings settings = makeValidOptOutSettings();
        settings.setPhoneNumber("   ");
        assertValidatorMessage(SmsOptOutSettingsValidator.INSTANCE, settings, "number", "is required");
    }

    private static SmsOptOutSettings makeValidOptOutSettings() {
        SmsOptOutSettings settings = SmsOptOutSettings.create();
        settings.setPhoneNumber(PHONE_NUMBER);
        return settings;
    }
}
