package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Collections;
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
        if (dataGroups != null) {
            if (!dataGroups.containsAll(criteria.getAllOfGroups())) {
                return false;
            }
            if (!Collections.disjoint(dataGroups, criteria.getNoneOfGroups())) {
                return false;
            }
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
    public static void validate(Criteria criteria, Set<String> dataGroups, Errors errors) {
        for (String osName : criteria.getAppVersionOperatingSystems()) {
            Integer minAppVersion = criteria.getMinAppVersion(osName);
            Integer maxAppVersion = criteria.getMaxAppVersion(osName);
            
            if (minAppVersion != null && maxAppVersion != null && maxAppVersion < minAppVersion) {
                pushSubpathError(errors, "maxAppVersions", osName, "cannot be less than minAppVersion");
            }
            if (minAppVersion != null && minAppVersion < 0) {
                pushSubpathError(errors, "minAppVersions", osName, "cannot be negative");
            }
            if (maxAppVersion != null && maxAppVersion < 0) {
                pushSubpathError(errors, "maxAppVersions", osName, "cannot be negative");
            }
        }
        validateDataGroups(errors, dataGroups, criteria.getAllOfGroups(), "allOfGroups");
        validateDataGroups(errors, dataGroups, criteria.getNoneOfGroups(), "noneOfGroups");
        validateDataGroupNotRequiredAndProhibited(criteria, errors);
    }
    
    private static void pushSubpathError(Errors errors, String subpath, String osName, String error) {
        errors.pushNestedPath(subpath);
        errors.rejectValue(BridgeUtils.textToErrorKey(osName), error);
        errors.popNestedPath();
    }

    // This is a simple match: if a criteria declares a language, the user must declare the language
    // This does NOT necessarily return the user's most desired language.
    private static boolean languageDoesNotMatch(Set<String> preferredLanguages, String targetLanguage) {
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
     * Can't logically have a data group that is both required and prohibited, so check for this.
     * @param criteria
     * @param errors
     */
    private static void validateDataGroupNotRequiredAndProhibited(Criteria criteria, Errors errors) {
        if (criteria.getAllOfGroups() != null && criteria.getNoneOfGroups() != null) {
            Set<String> intersection = Sets.intersection(criteria.getAllOfGroups(), criteria.getNoneOfGroups());
            if (!intersection.isEmpty()) {
                errors.rejectValue("allOfGroups", "includes these prohibited data groups: " + COMMA_SPACE_JOINER.join(intersection));
            }
        }
    }
    
    private static void validateDataGroups(Errors errors, Set<String> dataGroups, Set<String> criteriaGroups, String propName) {
        if (criteriaGroups == null) {
            errors.rejectValue(propName, "cannot be null");
        } else {
            for (String dataGroup : criteriaGroups) {
                if (!dataGroups.contains(dataGroup)) {
                    errors.rejectValue(propName, getDataGroupMessage(dataGroup, dataGroups));
                }
            }
        }
    }
    
    private static String getDataGroupMessage(String identifier, Set<String> dataGroups) {
        String message = "'" + identifier + "' is not in enumeration: ";
        if (dataGroups.isEmpty()) {
            message += "<no data groups declared>";
        } else {
            message += COMMA_SPACE_JOINER.join(dataGroups);
        }
        return message;
    }

}
