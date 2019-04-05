package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AccountSummarySearchValidator implements Validator {
    public static final String DATE_RANGE_ERROR = "startDate should be before endDate";
    public static final String PAGE_RANGE_ERROR = "must be from "+API_MINIMUM_PAGE_SIZE+"-"+API_MAXIMUM_PAGE_SIZE+" records";

    private Set<String> studyDataGroups;
    
    public AccountSummarySearchValidator(Set<String> studyDataGroups) {
        this.studyDataGroups = studyDataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AccountSummarySearch.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AccountSummarySearch search = (AccountSummarySearch)object;
        
        if (search.getOffsetBy() < 0) {
            errors.rejectValue("offsetBy", "cannot be less than 0");
        }
        // Just set a sane upper limit on this.
        if (search.getPageSize() < API_MINIMUM_PAGE_SIZE || search.getPageSize() > API_MAXIMUM_PAGE_SIZE) {
            errors.rejectValue("pageSize", PAGE_RANGE_ERROR);
        }
        if (search.getStartTime() != null && search.getEndTime() != null
                && search.getStartTime().getMillis() >= search.getEndTime().getMillis()) {
            errors.reject(DATE_RANGE_ERROR);
        }
        if (!search.getAllOfGroups().isEmpty()) {
            List<String> errorMessages = CriteriaUtils.validateSetItemsExist(studyDataGroups, search.getAllOfGroups());
            for (String errorMsg : errorMessages) {
                errors.rejectValue("allOfGroups", errorMsg);    
            }
        }
        if (!search.getNoneOfGroups().isEmpty()) {
            List<String> errorMessages = CriteriaUtils.validateSetItemsExist(studyDataGroups, search.getNoneOfGroups());
            for (String errorMsg : errorMessages) {
                errors.rejectValue("noneOfGroups", errorMsg);    
            }
        }
        String errorMessage = CriteriaUtils.validateSetItemsDoNotOverlap(
                "data groups", search.getAllOfGroups(), search.getNoneOfGroups());
        if (errorMessage != null) {
            errors.rejectValue("allOfGroups", errorMessage);
        }
    }
}
