package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

/** Validator for SMS opt-out settings. */
public class SmsOptOutSettingsValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final SmsOptOutSettingsValidator INSTANCE = new SmsOptOutSettingsValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return SmsOptOutSettings.class.isAssignableFrom(clazz);
    }

    /** Validates SMS opt-out settings. All fields are required. */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("smsOptOutSettings", "cannot be null");
        } else if (!(target instanceof SmsOptOutSettings)) {
            errors.rejectValue("smsOptOutSettings", "is the wrong type");
        } else {
            SmsOptOutSettings settings = (SmsOptOutSettings) target;

            // number
            if (StringUtils.isBlank(settings.getNumber())) {
                errors.rejectValue("number", "is required");
            }

            // Implementation ensures that all other fields are non-null.
        }
    }
}
