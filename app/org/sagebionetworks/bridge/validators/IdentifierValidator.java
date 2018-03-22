package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.accounts.Identifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class IdentifierValidator implements Validator {
    
    private final ChannelType type;
    
    public IdentifierValidator(ChannelType type) {
        this.type = type;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Identifier.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Identifier identifier = (Identifier)obj;

        if (identifier.getStudyIdentifier() == null) {
            errors.rejectValue("study", "is required");
        }
        if (type == ChannelType.EMAIL) {
            if (StringUtils.isBlank(identifier.getEmail())) {
                errors.rejectValue("email", "is required");
            }
        } else if (type == ChannelType.PHONE) {
            if (identifier.getPhone() == null) {
                errors.rejectValue("phone", "is required");
            } else if (!Phone.isValid(identifier.getPhone())) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
        }
    }
}
