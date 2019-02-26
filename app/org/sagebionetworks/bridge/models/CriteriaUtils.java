package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;

import com.google.common.collect.Sets;

/**
 * Utility classes for working with domain entities that should be matched by a growing list of criteria, such as the
 * version of the app making a request or the data groups associated to a user. Matching is currently done through
 * information passed in through the ScheduleContext, a parameter object of values against which matching occurs.
 */
public class CriteriaUtils {
    
    /**
     * Match the context of a request (the user's language and data groups, the application making the request) against
     * the criteria for including an object in the content that a user sees. Returns true if the object should be
     * included, and false otherwise.
     */
    public static boolean matchCriteria(CriteriaContext context, Criteria criteria) {
        checkNotNull(context);
        checkNotNull(context.getLanguages());
        checkNotNull(context.getClientInfo());
        checkNotNull(context.getUserDataGroups());
        checkNotNull(criteria.getAllOfGroups());
        checkNotNull(criteria.getNoneOfGroups());
        checkNotNull(criteria.getAllOfSubstudyIds());
        checkNotNull(criteria.getNoneOfSubstudyIds());
        
        Integer appVersion = context.getClientInfo().getAppVersion();
        String appOs = context.getClientInfo().getOsName();
        if (appVersion != null && appOs != null) {
            Integer minAppVersion = criteria.getMinAppVersion(appOs);
            Integer maxAppVersion = criteria.getMaxAppVersion(appOs);
            if ((minAppVersion != null && appVersion < minAppVersion) ||
                (maxAppVersion != null && appVersion > maxAppVersion)) {
                return false;
            }
        }
        Set<String> dataGroups = context.getUserDataGroups();
        if (!dataGroups.containsAll(criteria.getAllOfGroups())) {
            return false;
        }
        if (!Collections.disjoint(dataGroups, criteria.getNoneOfGroups())) {
            return false;
        }
        Set<String> substudies = context.getUserSubstudyIds();
        if (!substudies.containsAll(criteria.getAllOfSubstudyIds())) {
            return false;
        }
        if (!Collections.disjoint(substudies, criteria.getNoneOfSubstudyIds())) {
            return false;
        }
        if (languageDoesNotMatch(context.getLanguages(), criteria.getLanguage())) {
            return false;
        }
        return true;
    }

    /**
     * Validate that the criteria are correct (e.g. including the same data group in both required and prohibited sets,
     * or having a min-max version range out of order, are obviously incorrect because they can never match).
     */
    public static void validate(Criteria criteria, Set<String> dataGroups, Set<String> substudyIds, Errors errors) {
        for (String osName : criteria.getAppVersionOperatingSystems()) {
            Integer minAppVersion = criteria.getMinAppVersion(osName);
            Integer maxAppVersion = criteria.getMaxAppVersion(osName);
            String errorKey = BridgeUtils.textToErrorKey(osName);
            
            if (minAppVersion != null && maxAppVersion != null && maxAppVersion < minAppVersion) {
                pushSubpathError(errors, "maxAppVersions", errorKey, "cannot be less than minAppVersions."+errorKey);
            }
            if (minAppVersion != null && minAppVersion < 0) {
                pushSubpathError(errors, "minAppVersions", errorKey, "cannot be negative");
            }
            if (maxAppVersion != null && maxAppVersion < 0) {
                pushSubpathError(errors, "maxAppVersions", errorKey, "cannot be negative");
            }
        }
        validateSetItemsExist(errors, "allOfGroups", dataGroups, criteria.getAllOfGroups());
        validateSetItemsExist(errors, "noneOfGroups", dataGroups, criteria.getNoneOfGroups());
        validateSetItemsDoNotOverlap(errors, "data groups", "allOfGroups", criteria.getAllOfGroups(),
                criteria.getNoneOfGroups());
        
        validateSetItemsExist(errors, "allOfSubstudyIds", substudyIds, criteria.getAllOfSubstudyIds());
        validateSetItemsExist(errors, "noneOfSubstudyIds", substudyIds, criteria.getNoneOfSubstudyIds());
        validateSetItemsDoNotOverlap(errors, "substudies", "allOfSubstudyIds", criteria.getAllOfSubstudyIds(),
                criteria.getNoneOfSubstudyIds());
    }
    
    private static void pushSubpathError(Errors errors, String subpath, String errorKey, String error) {
        errors.pushNestedPath(subpath);
        errors.rejectValue(errorKey, error);
        errors.popNestedPath();
    }

    // This is a simple match: if a criteria declares a language, the user must declare the language
    // This does NOT necessarily return the user's most desired language.
    private static boolean languageDoesNotMatch(List<String> preferredLanguages, String targetLanguage) {
        // It doesn't match if 1) target language has been specified or 
        // 2) user has declared the required language. 
        if (targetLanguage == null) {
            return false;
        }
        for (String prefLang : preferredLanguages) {
            if (targetLanguage.equalsIgnoreCase(prefLang)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * This is called externally by AccountSummarySearchValidator.
     */
    public static List<String> validateSetItemsExist(Set<String> fullSet, Set<String> setItems){
        List<String> errors = new ArrayList<String>();
        if (setItems == null) {
            errors.add("cannot be null");
            return errors;
        }
        for (String item : setItems) {
            if (!fullSet.contains(item)) {
                String listStr = (fullSet.isEmpty()) ? "<empty>" : COMMA_SPACE_JOINER.join(fullSet);
                String message = String.format("'%s' is not in enumeration: %s", item, listStr);
                errors.add(message);
            }
        }
        return errors;
    }
    
    private static void validateSetItemsExist(Errors errors, String fieldName, Set<String> fullSet, Set<String> setItems) {
        List<String> errorMessages = validateSetItemsExist(fullSet, setItems);
        for (String errorMessage : errorMessages) {
            errors.rejectValue(fieldName, errorMessage);
        }
    }
    
    public static String validateSetItemsDoNotOverlap(String setItemTypeName, Set<String> setA, Set<String> setB) {
        if (setA != null && setB != null) {
            Set<String> intersection = Sets.intersection(setA, setB);
            if (!intersection.isEmpty()) {
                return "includes these excluded " + setItemTypeName + ": " + COMMA_SPACE_JOINER.join(intersection);
            }
        }
        return null;
    }
    
    /**
     * Can't logically have a data group that is both required and prohibited, so check for this.
     */
    private static void validateSetItemsDoNotOverlap(Errors errors, String setItemTypeName, String fieldName,
            Set<String> setA, Set<String> setB) {
        String errorMessage = validateSetItemsDoNotOverlap(setItemTypeName, setA, setB);
        if (errorMessage != null) {
            errors.rejectValue(fieldName, errorMessage);
        }
    }
}
