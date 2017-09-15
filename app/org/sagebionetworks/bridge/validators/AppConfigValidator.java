package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigValidator implements Validator {

    private Set<String> dataGroups;
    
    public AppConfigValidator(Set<String> dataGroups) {
        this.dataGroups = dataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfig appConfig = (AppConfig)object;
        
        if (appConfig.getStudyId() == null) {
            errors.rejectValue("studyId", "is required");
        }
        if (appConfig.getCriteria() == null) {
            errors.rejectValue("criteria", "are required");
        } else {
            CriteriaUtils.validate(appConfig.getCriteria(), dataGroups, errors);    
        }
    }
}
