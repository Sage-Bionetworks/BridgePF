package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.sms.IncomingSms;

/** Validator for IncomingSms. */
public class IncomingSmsValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final IncomingSmsValidator INSTANCE = new IncomingSmsValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return IncomingSms.class.isAssignableFrom(clazz);
    }

    /** Validates an incoming SMS object. All fields are required. (Message body can be blank.) */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("incomingSms", "cannot be null");
        } else if (!(target instanceof IncomingSms)) {
            errors.rejectValue("incomingSms", "is the wrong type");
        } else {
            IncomingSms incomingSms = (IncomingSms) target;

            // messageId
            if (StringUtils.isBlank(incomingSms.getMessageId())) {
                errors.rejectValue("messageId", "is required");
            }

            // body - Can't be null, but can be blank, since people might send a blank SMS.
            if (incomingSms.getBody() == null) {
                errors.rejectValue("body", "is required");
            }

            // senderNumber
            if (StringUtils.isBlank(incomingSms.getSenderNumber())) {
                errors.rejectValue("senderNumber", "is required");
            }
        }
    }
}
