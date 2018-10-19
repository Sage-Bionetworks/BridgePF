package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigElementValidator implements Validator {

    public static final AppConfigElementValidator INSTANCE = new AppConfigElementValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfigElement.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfigElement appConfigElement = (AppConfigElement)object;
        
        if (isBlank(appConfigElement.getId())) {
            errors.rejectValue("id", "is required");
        }
        if (appConfigElement.getRevision() == null) {
            errors.rejectValue("revision", "is required");
        } else if (appConfigElement.getRevision() < 0) {
            errors.rejectValue("revision", "cannot be negative");
        }
        if (appConfigElement.getData() == null) {
            errors.rejectValue("data", "is required");
        }
    }
}
