package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class StudyValidator implements Validator {
    public static final StudyValidator INSTANCE = new StudyValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Study.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Study study = (Study)obj;
        if (StringUtils.isBlank(study.getIdentifier())) {
            errors.rejectValue("identifier", "is null or blank");
        } else {
            if (!study.getIdentifier().matches("^[a-z0-9-]+$")) {
                errors.rejectValue("identifier", "must contain only lower-case letters and/or numbers with optional dashes");
            }
            if (study.getIdentifier().length() < 2) {
                errors.rejectValue("identifier", "must be at least 2 characters");
            }
        }
        if (StringUtils.isBlank(study.getName())) {
            errors.rejectValue("name", "is null or blank");
        }
        if (StringUtils.isBlank(study.getSupportEmail())) {
            errors.rejectValue("supportEmail", "is null or blank");
        }
        for (String userProfileAttribute : study.getUserProfileAttributes()) {
            if (UserProfile.FIXED_PROPERTIES.contains(userProfileAttribute)) {
                String msg = String.format("'%s' conflicts with existing user profile property", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
        }
        validateEmails(errors, study.getSupportEmail(), "supportEmail");
        validateEmails(errors, study.getConsentNotificationEmail(), "consentNotificationEmail");
    }
    
    private void validateEmails(Errors errors, String value, String fieldName) {
        Set<String> emails = commaListToSet(value);
        if (!emails.isEmpty()) {
            for (String email : emails) {
                if (!EmailValidator.getInstance().isValid(email)) {
                    errors.rejectValue(fieldName, "'%s' is not a valid email address", email);
                }
            }
        }
    }

    private Set<String> commaListToSet(String commaList) {
        return org.springframework.util.StringUtils.commaDelimitedListToSet(commaList);
    }
}
