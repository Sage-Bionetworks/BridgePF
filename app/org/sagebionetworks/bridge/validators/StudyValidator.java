package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
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
        if (StringUtils.isBlank(study.getTechnicalEmail())) {
            errors.rejectValue("technicalEmail", "is null or blank");
        }
        if (StringUtils.isBlank(study.getConsentNotificationEmail())) {
            errors.rejectValue("consentNotificationEmail", "is null or blank");
        }
        // These *should* be set if they are null, with defaults
        if (study.getPasswordPolicy() == null) {
            errors.rejectValue("passwordPolicy", "is null");
        } else {
            errors.pushNestedPath("passwordPolicy");
            PasswordPolicy policy = study.getPasswordPolicy();
            if (!isInRange(policy.getMinLength(), 2)) {
                errors.rejectValue("minLength", "must be at least 2 and no more than 100");
            }
            errors.popNestedPath();
        }
        validateTemplate(errors, study.getVerifyEmailTemplate(), "verifyEmailTemplate");
        validateTemplate(errors, study.getResetPasswordTemplate(), "resetPasswordTemplate");
        
        for (String userProfileAttribute : study.getUserProfileAttributes()) {
            if (UserProfile.FIXED_PROPERTIES.contains(userProfileAttribute)) {
                String msg = String.format("'%s' conflicts with existing user profile property", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
        }
        validateEmails(errors, study.getSupportEmail(), "supportEmail");
        validateEmails(errors, study.getTechnicalEmail(), "technicalEmail");
        validateEmails(errors, study.getConsentNotificationEmail(), "consentNotificationEmail");
    }
    
    private boolean isInRange(int value, int min) {
        return (value >= min && value <= 100);
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
    
    private void validateTemplate(Errors errors, EmailTemplate template, String fieldName) {
        if (template == null) {
            errors.rejectValue(fieldName, "is null");
        } else {
            errors.pushNestedPath(fieldName);
            if (StringUtils.isBlank(template.getSubject())) {
                errors.rejectValue("subject", "is null or blank");
            }
            if (StringUtils.isBlank(template.getBody())) {
                errors.rejectValue("body", "is null or blank");
            } else {
                if (!template.getBody().contains("${url}")) {
                    errors.rejectValue("body", "must contain the ${url} template variable");
                }
            }
            errors.popNestedPath();
        }
    }

    private Set<String> commaListToSet(String commaList) {
        return org.springframework.util.StringUtils.commaDelimitedListToSet(commaList);
    }
}
