package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.sms.SmsMessage;

/** Validator for SmsMessage. */
public class SmsMessageValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final SmsMessageValidator INSTANCE = new SmsMessageValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return SmsMessage.class.isAssignableFrom(clazz);
    }

    /** Validates an SmsMessage. All fields are required. */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("smsMessage", "cannot be null");
        } else if (!(target instanceof SmsMessage)) {
            errors.rejectValue("smsMessage", "is the wrong type");
        } else {
            SmsMessage message = (SmsMessage) target;

            // number
            if (StringUtils.isBlank(message.getPhoneNumber())) {
                errors.rejectValue("number", "is required");
            }

            // sentOn must be positive.
            if (message.getSentOn() <= 0) {
                errors.rejectValue("sentOn", "must be positive");
            }

            // messageBody
            if (StringUtils.isBlank(message.getMessageBody())) {
                errors.rejectValue("messageBody", "is required");
            }

            // messageId
            if (StringUtils.isBlank(message.getMessageId())) {
                errors.rejectValue("messageId", "is required");
            }

            // smsType
            if (message.getSmsType() == null) {
                errors.rejectValue("smsType", "is required");
            }

            // studyId
            if (StringUtils.isBlank(message.getStudyId())) {
                errors.rejectValue("studyId", "is required");
            }
        }
    }
}
