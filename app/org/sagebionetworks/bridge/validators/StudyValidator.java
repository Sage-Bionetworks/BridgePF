package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyValidator implements Validator {

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
            if (!study.getIdentifier().matches("^[a-z-]+$")) {
                errors.rejectValue("identifier", "must contain only lower-case letters with optional dashes");
            }
            if (study.getIdentifier().length() < 2) {
                errors.rejectValue("identifier", "must be at least 2 characters");
            }
        }
        if (StringUtils.isBlank(study.getName())) {
            errors.rejectValue("name", "is null or blank");
        }
    }

}
