package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.studies.Study2;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return Study2.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Study2 study = (Study2)obj;
        if (StringUtils.isBlank(study.getIdentifier())) {
            errors.reject("identifier", "is null or blank");
        } else {
            if (!study.getIdentifier().matches("^[a-z-]+$")) {
                errors.reject("identifier", "must contain only lower-case letters with optional dashes");
            }
            if (study.getIdentifier().length() < 2) {
                errors.reject("identifier", "must be at least 2 characters");
            }
        }
        if (StringUtils.isBlank(study.getName())) {
            errors.reject("name", "is null or blank");
        }
    }

}
