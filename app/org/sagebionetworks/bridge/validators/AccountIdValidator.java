package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * AccountIdValidator is used for those API calls where an API consumer sends us identifier 
 * information. We don't validate these objects with the validator in our own code.
 */
public class AccountIdValidator implements Validator {
    
    private static final AccountIdValidator EMAIL_INSTANCE = new AccountIdValidator(ChannelType.EMAIL);
    private static final AccountIdValidator PHONE_INSTANCE = new AccountIdValidator(ChannelType.PHONE);
    
    public static final AccountIdValidator getInstance(ChannelType type) {
        if (type == ChannelType.EMAIL) {
            return EMAIL_INSTANCE;
        } else if (type == ChannelType.PHONE) {
            return PHONE_INSTANCE;
        }
        throw new UnsupportedOperationException("Channel type not implemented");
    }
    
    private final ChannelType type;
    
    private AccountIdValidator(ChannelType type) {
        this.type = type;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AccountId.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        AccountId identifier = ((AccountId)obj).getUnguardedAccountId();
        
        if (identifier.getStudyId() == null) {
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
        } else {
            throw new UnsupportedOperationException("Channel type not implemented");
        }
    }
}
