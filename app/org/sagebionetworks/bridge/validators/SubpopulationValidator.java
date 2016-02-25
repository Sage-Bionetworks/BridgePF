package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

public class SubpopulationValidator implements Validator {

    private Set<String> dataGroups;
    
    public SubpopulationValidator(Set<String> dataGroups) {
        this.dataGroups = dataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Subpopulation.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Subpopulation subpop = (Subpopulation)object;
        
        if (subpop.getStudyIdentifier() == null) {
            errors.rejectValue("studyIdentifier", "is required");
        }
        if (isBlank(subpop.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (isBlank(subpop.getGuidString())) {
            errors.rejectValue("guid", "is required");
        }
        CriteriaUtils.validate(subpop.getCriteria(), dataGroups, errors);
    }
}
