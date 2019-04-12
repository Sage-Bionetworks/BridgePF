package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

public class SubpopulationValidator implements Validator {

    private Set<String> dataGroups;
    private Set<String> substudyIds;
    
    public SubpopulationValidator(Set<String> dataGroups, Set<String> substudyIds) {
        this.dataGroups = dataGroups;
        this.substudyIds = substudyIds;
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
        for (String dataGroup : subpop.getDataGroupsAssignedWhileConsented()) {
            if (!dataGroups.contains(dataGroup)) {
                String listStr = (dataGroups.isEmpty()) ? "<empty>" : COMMA_SPACE_JOINER.join(dataGroups);
                String message = String.format("'%s' is not in enumeration: %s", dataGroup, listStr);
                errors.rejectValue("dataGroupsAssignedWhileConsented", message);
            }
        }
        for (String substudyId : subpop.getSubstudyIdsAssignedOnConsent()) {
            if (!substudyIds.contains(substudyId)) {
                String listStr = (substudyIds.isEmpty()) ? "<empty>" : COMMA_SPACE_JOINER.join(substudyIds);
                String message = String.format("'%s' is not in enumeration: %s", substudyId, listStr);
                errors.rejectValue("substudyIdsAssignedOnConsent", message);
            }
        }
        CriteriaUtils.validate(subpop.getCriteria(), dataGroups, substudyIds, errors);
    }
}
