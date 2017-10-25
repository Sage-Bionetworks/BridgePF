package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

public class StudyParticipantValidator implements Validator {

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
            Phone phone = participant.getPhone();
            if (phone != null) {
                String phoneNumber = phone.getNumber(); 
                String phoneRegion = phone.getRegionCode();
                if (isNotBlank(phoneNumber) && isBlank(phoneRegion)) {
                    errors.rejectValue("phoneRegion", "is required if phone is provided");
                } else if (isNotBlank(phoneRegion) && isBlank(phoneNumber)) {
                    errors.rejectValue("phone", "is required if phoneRegion is provided");
                } else if (phoneRegion.length() != 2) {
                    errors.rejectValue("phoneRegion", "is not a two letter region code");
                } else  if (phone.getCanonicalPhone() == null) {
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
