package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.accounts.VerificationToken;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class VerificationTokenValidator implements Validator {
    
    public static final VerificationTokenValidator INSTANCE = new VerificationTokenValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return VerificationToken.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        VerificationToken email = (VerificationToken)obj;

        if (isBlank(email.getToken())) {
            errors.rejectValue("token", "is required");
        }
    }

}
