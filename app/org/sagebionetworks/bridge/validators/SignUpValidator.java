package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.validation.Errors;

public class SignUpValidator extends DataGroupsValidator {
    
    private final EmailValidator emailValidator = EmailValidator.getInstance();
    
    private final PasswordPolicy passwordPolicy;
    
    public SignUpValidator(PasswordPolicy passwordPolicy, Set<String> dataGroups) {
        super(dataGroups);
        this.passwordPolicy = passwordPolicy;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyParticipant.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyParticipant signUp = (StudyParticipant) object;
        
        super.validate(new DataGroups(signUp.getDataGroups()), errors);

        if (StringUtils.isBlank(signUp.getEmail())) {
            errors.rejectValue("email", "is required");
        } else if (!emailValidator.isValid(signUp.getEmail())){
            errors.rejectValue("email", "must be a valid email address");
        }
        if (StringUtils.isBlank(signUp.getPassword())) {
            errors.rejectValue("password", "is required");
            return;
        }
        // validate password
        String password = signUp.getPassword();
        if (passwordPolicy.getMinLength() > 0 && password.length() < passwordPolicy.getMinLength()) {
            errors.rejectValue("password", "must be at least "+passwordPolicy.getMinLength()+" characters");
        }
        if (passwordPolicy.isNumericRequired() && !password.matches(".*\\d+.*")) {
            errors.rejectValue("password", "must contain at least one number (0-9)");
        }
        if (passwordPolicy.isSymbolRequired() && !password.matches(".*\\p{Punct}+.*")) {
            errors.rejectValue("password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
        }
        if (passwordPolicy.isLowerCaseRequired() && !password.matches(".*[a-z]+.*")) {
            errors.rejectValue("password", "must contain at least one lowercase letter (a-z)");
        }
        if (passwordPolicy.isUpperCaseRequired() && !password.matches(".*[A-Z]+.*")) {
            errors.rejectValue("password", "must contain at least one uppercase letter (A-Z)");
        }
    }

}
