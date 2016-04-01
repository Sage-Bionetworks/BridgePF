package org.sagebionetworks.bridge.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.google.common.collect.Sets;

/**
 * A list of identifiers must consist entirely of valid identifiers.
 *
 */
public class ExternalIdsValidator implements Validator {

    private static final String IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
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
        } else if (identifiers.size() > addLimit) {
            errors.reject("contains too many elements; size=" + identifiers.size() + ", limit=" + addLimit);
        } else {
            Set<String> contents = Sets.newHashSet();
            for (int i=0; i < identifiers.size(); i++) {
                String id = identifiers.get(i);
                String path = "ids["+i+"]";
                
                if (contents.contains(id)) {
                    errors.rejectValue(path, "is a duplicate value");
                }
                contents.add(id);
                if (StringUtils.isBlank(id)) {
                    errors.rejectValue(path, "cannot be null or blank");
                } else if (!id.matches(IDENTIFIER_PATTERN)) {
                    String msg = String.format("'%s' must contain only digits, letters, underscores and dashes", id);
                    errors.rejectValue(path, msg);
                }
            }
        }
     }
}
