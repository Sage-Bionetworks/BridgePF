package org.sagebionetworks.bridge.validators;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * A list of identifiers must consist entirely of valid identifiers.
 *
 */
public class ExternalIdsValidator implements Validator {
    
    /**
     * Really the only purpose of this class is to provide a comprehensible name for the 
     * array that was submitted by the user, as "ArrayList" or somesuch is in Java-land, 
     * it's not JSON.
     */
    @SuppressWarnings("serial")
    public static class ExternalIdList extends ArrayList<String> implements BridgeEntity {
        public ExternalIdList(List<String> members) {
            super(members);
        }
    }

    private static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    private final int addLimit;
    
    public ExternalIdsValidator(int addLimit) {
        this.addLimit = addLimit;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ExternalIdList.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void validate(Object object, Errors errors) {
        ExternalIdList identifiers = (ExternalIdList) object;

        if (identifiers.isEmpty()) {
            errors.reject("contains no elements");
        }
        if (identifiers.size() > addLimit) {
            errors.reject("contains too many elements; size=" + identifiers.size() + ", limit=" + addLimit);
        } else {
            for (int i=0; i < identifiers.size(); i++) {
                String id = identifiers.get(i);
                String path = "ids["+i+"]";
                if (StringUtils.isBlank(id)) {
                    errors.rejectValue(path, "cannot be null or blank");
                } else if (!id.matches(SYNAPSE_IDENTIFIER_PATTERN)) {
                    String msg = String.format("'%s' must contain only digits, letters, underscores and dashes", id);
                    errors.rejectValue(path, msg);
                }
            }
        }
     }
}
