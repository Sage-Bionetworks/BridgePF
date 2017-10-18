package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.StringUtils.commaDelimitedListToSet;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

import org.springframework.core.annotation.AnnotationUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BridgeUtils {
    
    public static final Joiner COMMA_SPACE_JOINER = Joiner.on(", ");
    public static final Joiner COMMA_JOINER = Joiner.on(",");
    public static final Joiner SEMICOLON_SPACE_JOINER = Joiner.on("; ");
    public static final Joiner SPACE_JOINER = Joiner.on(" ");
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Create a variable map for the <code>resolveTemplate</code> method that includes common values from 
     * a study that used in most of our templates. The map is mutable. Variables include:
     * <ul>
     *  <li>studyName = study.getName()</li>
     *  <li>studyId = study.getIdentifier()</li>
     *  <li>sponsorName = study.getSponsorName()</li>
     *  <li>supportEmail = study.getSupportEmail()</li>
     *  <li>technicalEmail = study.getTechnicalEmail()</li>
     *  <li>consentEmail = study.getConsentNotificationEmail()</li>
     * </ul>
     */
    public static Map<String,String> studyTemplateVariables(Study study, Function<String,String> escaper) {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("studyId", study.getIdentifier());
        map.put("sponsorName", study.getSponsorName());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("consentEmail", study.getConsentNotificationEmail());
        if (escaper != null) {
            for (Map.Entry<String,String> entry : map.entrySet()) {
                map.put(entry.getKey(), escaper.apply(entry.getValue()));
            }
        }
        return map;
    }
    
    public static Map<String,String> studyTemplateVariables(Study study) {
        return studyTemplateVariables(study, null);
    }
    
    /**
     * A simple means of providing template variables in template strings, in the format <code>${variableName}</code>.
     * This value will be replaced with the value of the variable name. The variable name/value pairs are passed to the
     * method as a map. Variables that are not found in the map will be left in the string as is.
     *
     * @see https://sagebionetworks.jira.com/wiki/display/BRIDGE/EmailTemplate
     * 
     * @param template
     * @param values
     * @return
     */
    public static String resolveTemplate(String template, Map<String,String> values) {
        checkNotNull(template);
        checkNotNull(values);
        
        for (Map.Entry<String,String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                String var = "${"+entry.getKey()+"}";
                template = template.replace(var, entry.getValue());
            }
        }
        return template;
    }
    
    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
    
    /** Generate a random 16-byte salt, using a {@link SecureRandom}. */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Searches for a @BridgeTypeName annotation on this or any parent class in the class hierarchy, returning 
     * that value as the type name. If none exists, defaults to the simple class name. 
     * @param clazz
     * @return
     */
    public static String getTypeName(Class<?> clazz) {
        BridgeTypeName att = AnnotationUtils.findAnnotation(clazz,BridgeTypeName.class);
        if (att != null) {
            return att.value();
        }
        return clazz.getSimpleName();
    }
    
    /**
     * All batch methods in Dynamo return a list of failures rather than 
     * throwing an exception. We should have an exception specifically for 
     * these so the caller gets a list of items back, but for now, convert 
     * to a generic exception;
     * @param failures
     */
    public static void ifFailuresThrowException(List<FailedBatch> failures) {
        if (!failures.isEmpty()) {
            List<String> messages = Lists.newArrayList();
            for (FailedBatch failure : failures) {
                String message = failure.getException().getMessage();
                messages.add(message);
                String ids = Joiner.on("; ").join(failure.getUnprocessedItems().keySet());
                messages.add(ids);
            }
            throw new BridgeServiceException(Joiner.on(", ").join(messages));
        }
    }
    
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }
    
    public static <S,T> Map<S,T> asMap(List<T> list, Function<T,S> function) {
        Map<S,T> map = Maps.newHashMap();
        if (list != null && function != null) {
            for (T item : list) {
                map.put(function.apply(item), item);
            }
        }
        return map;
    }
    
    public static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            throw new RuntimeException("'" + value + "' is not a valid integer");
        }
    }

    public static Set<String> commaListToOrderedSet(String commaList) {
        if (commaList != null) {
            // This implementation must return a LinkedHashSet. This is a set
            // with ordered keys, in the order they were in the string, as some
            // set serializations depend on the order of the keys (languages).
            return commaDelimitedListToSet(commaList).stream()
                    .map(string -> string.trim())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // Cannot make this immutable without losing the concrete type we rely 
        // upon to ensure they keys are in the order they are inserted.
        return new LinkedHashSet<String>();
    }
    
    public static String setToCommaList(Set<String> set) {
        if (set != null) {
            // User LinkedHashSet because some supplied sets will have ordered keys 
            // and we want to preserve that order while processing the set. 
            Set<String> result = set.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return (result.isEmpty()) ? null : COMMA_JOINER.join(result);
        }
        return null;
    }
    
    /**
     * Wraps a set in an immutable set, or returns an empty immutable set if null.
     * @param set
     * @return
     */
    public @Nonnull static <T> ImmutableSet<T> nullSafeImmutableSet(Set<T> set) {
        return (set == null) ? ImmutableSet.of() : ImmutableSet.copyOf(set.stream()
                .filter(element -> element != null).collect(Collectors.toSet()));
    }
    
    public @Nonnull static <T> ImmutableList<T> nullSafeImmutableList(List<T> list) {
        return (list == null) ? ImmutableList.of() : ImmutableList.copyOf(list.stream()
                .filter(element -> element != null).collect(Collectors.toList()));
    }
    
    public @Nonnull static <S,T> ImmutableMap<S,T> nullSafeImmutableMap(Map<S,T> map) {
        ImmutableMap.Builder<S, T> builder = new ImmutableMap.Builder<>();
        if (map != null) {
            for (S key : map.keySet()) {
                if (map.get(key) != null) {
                    builder.put(key, map.get(key));
                }
            }
        }
        return builder.build();
    }
    
    /**
     * Converts a string to an error key friendly string, e.g. "iPhone OS" is converted to "iphone_os".
     * 
     * @throws IllegalArgumentException
     *             if the string cannot be converted to an error key.
     */
    public static String textToErrorKey(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("String is not translatable to an error key: " + text);
        }
        return text.toLowerCase().replaceAll(" ", "_").replaceAll("[^a-zA-Z0-9_-]", "");    
    }
    
    /**
     * Parse the string as an integer value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable integer, thrown a BadRequestException.
     */
    public static int getIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseInt(value);
        } catch(NumberFormatException e) {
            throw new BadRequestException(value + " is not an integer");
        }
    }

    /**
     * Parse the string as a long value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable long, thrown a BadRequestException.
     */
    public static Long getLongOrDefault(String value, Long defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseLong(value);
        } catch(RuntimeException e) {
            throw new BadRequestException(value + " is not a long");
        }
    }
    
    /**
     * Parse the string as a DateTime value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable DateTime, thrown a BadRequestException.
     */
    public static DateTime getDateTimeOrDefault(String value, DateTime defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return DateTime.parse(value);
        } catch(Exception e) {
            throw new BadRequestException(value + " is not a DateTime value");
        }
    }
    
    public static LocalDate getLocalDateOrDefault(String value, LocalDate defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        } else {
            try {
                return DateUtils.parseCalendarDate(value);
            } catch (RuntimeException ex) {
                throw new BadRequestException(value + " is not a LocalDate value");
            }
        }
    }
    
    /**
     * Creates a new copy of the map, removing any entries that have a null value (particularly easy to do this in
     * JSON).
     */
    public static <K,V> Map<K,V> withoutNullEntries(Map<K,V> map) {
        checkNotNull(map);
        return map.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
    }

    /** Helper method which puts something to a map, or removes it from the map if the value is null. */
    public static <K,V> void putOrRemove(Map<K,V> map, K key, V value) {
        checkNotNull(map);
        checkNotNull(key);
        if (value != null) {
            map.put(key, value);
        } else {
            map.remove(key);
        }
    }
    
    public static String encodeURIComponent(String component) {
        String encoded = null;
        if (component != null) {
            try {
                encoded = URLEncoder.encode(component, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is always supported, so this should never happen. 
                throw new BridgeServiceException(e.getMessage());
            }
        }
        return encoded;
    }
    
    public static String passwordPolicyDescription(PasswordPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Password must be ").append(policy.getMinLength()).append(" or more characters");
        if (policy.isLowerCaseRequired() || policy.isNumericRequired() || policy.isSymbolRequired() || policy.isUpperCaseRequired()) {
            sb.append(", and must contain at least ");
            List<String> phrases = new ArrayList<>();
            if (policy.isLowerCaseRequired()) {
                phrases.add("one lower-case letter");
            }
            if (policy.isUpperCaseRequired()) {
                phrases.add("one upper-case letter");
            }
            if (policy.isNumericRequired()) {
                phrases.add("one number");
            }
            if (policy.isSymbolRequired()) {
                // !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~
                phrases.add("one symbolic character (non-alphanumerics like #$%&@)");
            }
            for (int i=0; i < phrases.size(); i++) {
                if (i == phrases.size()-1) {
                    sb.append(", and ");
                } else if (i > 0) {
                    sb.append(", ");
                }
                sb.append(phrases.get(i));
            }
        }
        sb.append(".");
        return sb.toString();
    }
    
    public static String extractPasswordFromURI(URI uri) {
        boolean hasPassword = (uri.getUserInfo() != null && uri.getUserInfo().contains(":"));
        
        return (hasPassword) ? uri.getUserInfo().split(":")[1] : null;
    }
    
    public static String createReferentGuidIndex(ActivityType type, String guid, String localDateTime) {
        checkNotNull(type);
        checkNotNull(guid);
        checkNotNull(localDateTime);
        return String.format("%s:%s:%s", guid , type.name().toLowerCase(), localDateTime);
    }
    
    public static String createReferentGuidIndex(Activity activity, LocalDateTime localDateTime) {
        checkNotNull(activity);
        checkNotNull(localDateTime);
        
        ActivityType type = activity.getActivityType();
        String timestamp = localDateTime.toString();
        
        switch(type) {
        case COMPOUND:
            return createReferentGuidIndex(type, activity.getCompoundActivity().getTaskIdentifier(), timestamp);
        case SURVEY:
            return createReferentGuidIndex(type, activity.getSurvey().getGuid(), timestamp);
        case TASK:
            return createReferentGuidIndex(type, activity.getTask().getIdentifier(), timestamp);
        }
        throw new BridgeServiceException("Invalid activityType specified");    
    }

}
