package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.Email;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class EmailValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return Email.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Email email = (Email)obj;
        
        if (StringUtils.isBlank(email.getEmail())) {
            errors.rejectValue("email", "required");
        }
        // Eventually study will be required as well.
    }
    

}
