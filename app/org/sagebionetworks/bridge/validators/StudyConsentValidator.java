package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyConsentValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return StudyConsentForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyConsentForm consent = (StudyConsentForm)object;

        if (StringUtils.isBlank(consent.getPath())) {
            errors.reject("path", "is null or blank");
        } else if (consent.getMinAge() <= 0) {
            errors.reject("minAge", "must be > 0");
        }
    }

}
