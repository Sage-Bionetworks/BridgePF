package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Collections;
import java.util.Set;

import org.springframework.validation.Errors;

import com.google.common.collect.Sets;

/**
 * Utility classes for working with domain entities that should be matched by a growing list of 
 * criteria, such as the version of the app making a request or the data groups associated to 
 * a user. Matching is currently done through information passed in through the ScheduleContext, 
 * a parameter object of values against which matching occurs.
 */
public class CriteriaUtils {
    
    /**
     * A matching method that matches our common set of matching criteria for consents, schedulses, and more. 
     * We use the dataGroups and app version in the scheduling context and compare this to required and/or 
     * prohibitied data groups, and an application version range, to determine if there is a match or not. 
     */
    public static boolean matchCriteria(CriteriaContext context, Criteria criteria) {
        checkNotNull(context);
        checkNotNull(context.getLanguages());
        checkNotNull(context.getClientInfo());
        checkNotNull(context.getUserDataGroups());
        checkNotNull(criteria.getAllOfGroups());
        checkNotNull(criteria.getNoneOfGroups());
        
        Integer appVersion = context.getClientInfo().getAppVersion();
        if (appVersion != null) {
            Integer minAppVersion = criteria.getMinAppVersion();
            Integer maxAppVersion = criteria.getMaxAppVersion();
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
        if (langDoesNotMatch(context.getLanguages(), criteria.getLanguage())) {
            return false;
        }
        return true;
    }

    public static void validate(Criteria criteria, Set<String> dataGroups, Errors errors) {
        if ((criteria.getMinAppVersion() != null && criteria.getMaxAppVersion() != null) && 
            (criteria.getMaxAppVersion() < criteria.getMinAppVersion())) {
                errors.rejectValue("maxAppVersion", "cannot be less than minAppVersion");
        }
        if (criteria.getMinAppVersion() != null && criteria.getMinAppVersion() < 0) {
            errors.rejectValue("minAppVersion", "cannot be negative");
        }
        if (criteria.getMaxAppVersion() != null && criteria.getMaxAppVersion() < 0) {
            errors.rejectValue("maxAppVersion", "cannot be negative");
        }
        validateDataGroups(errors, dataGroups, criteria.getAllOfGroups(), "allOfGroups");
        validateDataGroups(errors, dataGroups, criteria.getNoneOfGroups(), "noneOfGroups");
        validateDataGroupNotRequiredAndProhibited(criteria, errors);
    }

    // This is a simple match: if a criteria declares a language, the user must declare the language
    // This does NOT necessarily return the user's most desired language.
    private static boolean langDoesNotMatch(Set<String> preferredLanguages, String targetLanguage) {
        if (targetLanguage == null) {
            return false;
        }
        for (String prefLang : preferredLanguages) {
            if (targetLanguage.equalsIgnoreCase(prefLang)) {
                return false;
            }
        }
        // It doesn't match if 1) target language has been specified and 2) user doesn't ask for it
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
