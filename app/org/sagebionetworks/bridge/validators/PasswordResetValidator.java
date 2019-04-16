package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class PasswordResetValidator implements Validator {

    private StudyService studyService;
    
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return PasswordReset.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        PasswordReset passwordReset = (PasswordReset)object;
        
        if (StringUtils.isBlank(passwordReset.getSptoken())) {
            errors.rejectValue("sptoken", "is required");
        }
        if (StringUtils.isBlank(passwordReset.getPassword())) {
            errors.rejectValue("password", "is required");
        }
        if (StringUtils.isBlank(passwordReset.getStudyIdentifier())) {
            errors.rejectValue("study", "is required");
        }
        if (errors.hasErrors()) {
            return;
        }
        // This logic is now duplicated with StudyParticipant validation.
        Study study = studyService.getStudy(passwordReset.getStudyIdentifier());
        PasswordPolicy passwordPolicy = study.getPasswordPolicy();
        String password = passwordReset.getPassword();
        ValidatorUtils.validatePassword(errors, passwordPolicy, password);
    }
}
