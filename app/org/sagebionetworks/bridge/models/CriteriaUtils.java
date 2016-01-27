package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;
import java.util.Set;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;

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
     * @param context
     * @param required
     * @param prohibited
     * @param minAppVersion
     * @param maxAppVersion
     * @return
     */
    public static boolean matchCriteria(ScheduleContext context, Criteria criteria) {
        checkNotNull(context);
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
            for (String group : criteria.getNoneOfGroups()) {
                if (dataGroups.contains(group)) {
                    return false;
                }
            }
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

    /**
     * Can't logically have a data group that is both required and prohibited, so check for this.
     * @param criteria
     * @param errors
     */
    private static void validateDataGroupNotRequiredAndProhibited(Criteria criteria, Errors errors) {
        if (criteria.getAllOfGroups() != null && criteria.getNoneOfGroups() != null) {
            Set<String> intersection = Sets.newHashSet(criteria.getAllOfGroups());
            intersection.retainAll(criteria.getNoneOfGroups());
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
