package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

public class StudyParticipantValidator implements Validator {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    private final Study study;
    private final boolean isNew;
    
    public StudyParticipantValidator(Study study, boolean isNew) {
        this.study = study;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyParticipant.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyParticipant participant = (StudyParticipant)object;
        
        if (isNew) {
            if (isBlank(participant.getEmail())) {
                errors.rejectValue("email", "is required");
            } else if (!EMAIL_VALIDATOR.isValid(participant.getEmail())){
                errors.rejectValue("email", "must be a valid email address");
            }
            if (study.isExternalIdRequiredOnSignup() && isBlank(participant.getExternalId())) {
                errors.rejectValue("externalId", "is required");
            }
            // Validate phone number. We currently don't allow phone number to be updated, so only do this
            // on a new account.
            String phone = participant.getPhone();
            String phoneRegion = participant.getPhoneRegion();
            
            if (isBlank(phone) && isBlank(phoneRegion)) {
                // Do nothing. This is okay. Phone is optional right now, we don't use it.
            } else if (isNotBlank(phone) && isBlank(phoneRegion)) {
                errors.rejectValue("phoneRegion", "is required if phone is provided");
            } else if (isNotBlank(phoneRegion) && isBlank(phone)) {
                errors.rejectValue("phone", "is required if phoneRegion is provided");
            } else if (phoneRegion.length() != 2) {
                errors.rejectValue("phoneRegion", "is not a two letter region code");
            } else {
                try {
                    PhoneNumber phoneNumber = PHONE_UTIL.parse(phone, phoneRegion);
                    if (!PHONE_UTIL.isValidNumber(phoneNumber)) {
                        errors.rejectValue("phone", "does not appear to be a phone number");
                    }
                } catch (NumberParseException e) {
                    errors.rejectValue("phone", "does not appear to be a phone number");
                }
            }
            // This validation logic is also performed for reset password requests.
            String password = participant.getPassword();
            PasswordPolicy passwordPolicy = study.getPasswordPolicy();
            ValidatorUtils.validatePassword(errors, passwordPolicy, password);
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }
        
        // if external ID validation is enabled, it's not covered by the validator.
        for (String dataGroup : participant.getDataGroups()) {
            if (!study.getDataGroups().contains(dataGroup)) {
                errors.rejectValue("dataGroups", messageForSet(study.getDataGroups(), dataGroup));
            }
        }
        for (String attributeName : participant.getAttributes().keySet()) {
            if (!study.getUserProfileAttributes().contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(study.getUserProfileAttributes(), attributeName));
            }
        }
    }
    
    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for study (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}
