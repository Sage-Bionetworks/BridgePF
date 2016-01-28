package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.accounts.DataGroups;

public class DataGroupsValidator implements Validator {

    private final Set<String> studyDataGroups;
    
    public DataGroupsValidator(Set<String> studyDataGroups) {
        this.studyDataGroups = studyDataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return DataGroups.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        DataGroups dataGroups = (DataGroups)object;
        
        for (String group : dataGroups.getDataGroups()) {
            if (!studyDataGroups.contains(group)) {
                errors.rejectValue("dataGroups", getDataGroupMessage(group));
            }
        }
        
    }

    private String getDataGroupMessage(String group) {
        String message = "'" + group + "' is not one of these valid values: ";
        if (studyDataGroups.isEmpty()) {
            message += "<none>";
        } else {
            message += COMMA_SPACE_JOINER.join(studyDataGroups) + ".";
        }
        return message;
    }
}
