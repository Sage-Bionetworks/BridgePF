package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

public class StudyParticipantValidator implements Validator {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    private final ExternalIdService externalIdService;
    private final Study study;
    private final boolean isNew;
    
    public StudyParticipantValidator(ExternalIdService externalIdService, Study study, boolean isNew) {
        this.externalIdService = externalIdService;
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
            Phone phone = participant.getPhone();
            String email = participant.getEmail();
            String externalId = participant.getExternalId();
            if (email == null && isBlank(externalId) && phone == null) {
                errors.reject("email, phone, or externalId is required");
            }
            // If provided, phone must be valid
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
            // If provided, email must be valid
            if (email != null && !EMAIL_VALIDATOR.isValid(email)) {
                errors.rejectValue("email", "does not appear to be an email address");
            }
            if (study.isExternalIdRequiredOnSignup() && isBlank(participant.getExternalId())) {
                errors.rejectValue("externalId", "is required");
            }
            // Password is optional, but validation is applied if supplied, any time it is 
            // supplied (such as in the password reset workflow).
            String password = participant.getPassword();
            if (password != null) {
                PasswordPolicy passwordPolicy = study.getPasswordPolicy();
                ValidatorUtils.validatePassword(errors, passwordPolicy, password);
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }
        // External ID can be updated during creation or on update. We validate it if IDs are 
        // managed. If it's already assigned to another user, the database constraints will 
        // prevent this record's persistence.
        if (study.isExternalIdValidationEnabled() && StringUtils.isNotBlank(participant.getExternalId())) {
            ExternalIdentifier externalId = externalIdService.getExternalId(study.getStudyIdentifier(),
                    participant.getExternalId());
            if (externalId == null) {
                errors.rejectValue("externalId", "is not a valid external ID");
            }
        }
        // Never okay to have a blank external ID. It can produce errors later when querying for ext IDs
        if (participant.getExternalId() != null && StringUtils.isBlank(participant.getExternalId())) {
            errors.rejectValue("externalId", "cannot be blank");
        }
                
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
