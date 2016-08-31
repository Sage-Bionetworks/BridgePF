package org.sagebionetworks.bridge.upload;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.schema.SchemaUtils;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    private static final Logger logger = LoggerFactory.getLogger(UploadUtil.class);

    /*
     * Suffix used for unit fields in schemas. For example, if we had a field called "jogtime", we would have a field
     * called "jogtime_unit".
     */
    public static final String UNIT_FIELD_SUFFIX = "_unit";

    /** Utility method for canonicalizing an upload JSON value given the schema's field type. */
    public static CanonicalizationResult canonicalize(final JsonNode valueNode, UploadFieldType type) {
        if (valueNode == null || valueNode.isNull()) {
            // Short-cut: Don't do anything if the value is Java null (non-existent) or JSON null.
            return CanonicalizationResult.makeResult(valueNode);
        }

        switch (type) {
            case ATTACHMENT_BLOB:
            case ATTACHMENT_CSV:
            case ATTACHMENT_JSON_BLOB:
            case ATTACHMENT_JSON_TABLE:
            case ATTACHMENT_V2:
            case INLINE_JSON_BLOB: {
                // always valid, always canonical
                return CanonicalizationResult.makeResult(valueNode);
            }
            case BOOLEAN: {
                if (valueNode.isIntegralNumber()) {
                    // For numbers, 0 is false and everything else is true.
                    boolean booleanValue = valueNode.intValue() != 0;
                    return CanonicalizationResult.makeResult(BooleanNode.valueOf(booleanValue));
                } else if (valueNode.isTextual()) {
                    // We accept "true" and "false" (ignoring case), but not anything else.
                    String boolStr = valueNode.textValue();
                    if ("false".equalsIgnoreCase(boolStr)) {
                        return CanonicalizationResult.makeResult(BooleanNode.FALSE);
                    } else if ("true".equalsIgnoreCase(boolStr)) {
                        return CanonicalizationResult.makeResult(BooleanNode.TRUE);
                    } else {
                        return CanonicalizationResult.makeError("Invalid boolean string " + boolStr);
                    }
                } else if (valueNode.isBoolean()) {
                    // This is already canonicalized.
                    return CanonicalizationResult.makeResult(valueNode);
                } else {
                    return CanonicalizationResult.makeError("Invalid boolean JSON value " + valueNode.toString());
                }
            }
            case CALENDAR_DATE: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid calendar date JSON value " +
                            valueNode.toString());
                }

                // parseIosCalendarDate() will truncate full date-times to calendar dates as needed.
                String dateStr = valueNode.textValue();
                LocalDate parsedDate = parseIosCalendarDate(dateStr);

                if (parsedDate != null) {
                    return CanonicalizationResult.makeResult(new TextNode(DateUtils.getCalendarDateString(
                            parsedDate)));
                } else {
                    return CanonicalizationResult.makeError("Invalid calendar date string " + dateStr);
                }
            }
            case DURATION_V2: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid duration JSON value " + valueNode.toString());
                }

                String durationStr = valueNode.textValue();
                try {
                    // Joda Duration only parses seconds and milliseconds. Use Period to get an ISO 8601 duration.
                    // Period.parse() never returns null.
                    Period parsedPeriod = Period.parse(durationStr);
                    return CanonicalizationResult.makeResult(new TextNode(parsedPeriod.toString()));
                } catch (IllegalArgumentException ex) {
                    return CanonicalizationResult.makeError("Invalid duration string " + durationStr);
                }
            }
            case FLOAT: {
                if (valueNode.isNumber()) {
                    // Already canonicalized.
                    return CanonicalizationResult.makeResult(valueNode);
                } else if (valueNode.isTextual()) {
                    // Convert to decimal.
                    String decimalStr = valueNode.textValue();
                    try {
                        BigDecimal parsedDecimal = new BigDecimal(decimalStr);
                        return CanonicalizationResult.makeResult(new DecimalNode(parsedDecimal));
                    } catch (IllegalArgumentException ex) {
                        return CanonicalizationResult.makeError("Invalid decimal string " + decimalStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid decimal JSON value " + valueNode.toString());
                }
            }
            case INT: {
                if (valueNode.isIntegralNumber()) {
                    // Already canonicalized
                    return CanonicalizationResult.makeResult(valueNode);
                } else if (valueNode.isFloatingPointNumber()) {
                    // Convert floats to ints.
                    return CanonicalizationResult.makeResult(new BigIntegerNode(valueNode.bigIntegerValue()));
                } else if (valueNode.isTextual()) {
                    // Parse as a big decimal, truncate to big int.
                    String numberStr = valueNode.textValue();
                    try {
                        BigDecimal parsedNumber = new BigDecimal(numberStr);
                        return CanonicalizationResult.makeResult(new BigIntegerNode(parsedNumber.toBigInteger()));
                    } catch (IllegalArgumentException ex) {
                        return CanonicalizationResult.makeError("Invalid int string " + numberStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid int JSON value " + valueNode.toString());
                }
            }
            case MULTI_CHOICE: {
                // Expect it in the format ["foo", "bar", "baz"]
                if (!valueNode.isArray()) {
                    return CanonicalizationResult.makeError("Invalid multi-choice JSON value " + valueNode.toString());
                }

                // Fields inside might not be strings. Trivially convert them to strings if they are not.
                ArrayNode convertedValueNode = BridgeObjectMapper.get().createArrayNode();
                int numValues = valueNode.size();
                for (int i = 0; i < numValues; i++) {
                    // Sanitize the multi-choice answers so they match up with the field def's multi-choice answer list
                    String rawAnswer = getAsString(valueNode.get(i));
                    String sanitizedAnswer = SchemaUtils.sanitizeFieldName(rawAnswer);
                    convertedValueNode.add(sanitizedAnswer);
                }

                return CanonicalizationResult.makeResult(convertedValueNode);
            }
            case SINGLE_CHOICE: {
                // Older versions would send a single-element array (example: ["foo"]) as a single-choice answer. For
                // backwards compatibility, accept arrays, but use just the single element.
                JsonNode convertedValueNode;
                if (valueNode.isArray()) {
                    if (valueNode.size() == 1) {
                        convertedValueNode = valueNode.get(0);
                    } else {
                        return CanonicalizationResult.makeError("Single-choice array doesn't have exactly 1 element: "
                                + valueNode.toString());
                    }
                } else {
                    // Not an array. Pass this straight through to the next step.
                    convertedValueNode = valueNode;
                }

                // If the value isn't a string, trivially convert it into a string.
                return CanonicalizationResult.makeResult(convertToStringNode(convertedValueNode));
            }
            case STRING: {
                // If the value isn't a string, trivially convert it into a string.
                return CanonicalizationResult.makeResult(convertToStringNode(valueNode));
            }
            case TIME_V2: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid time JSON value " + valueNode.toString());
                }

                // This is a time without date or time-zone, akin to Joda LocalTime. First parse it as a LocalTime.
                String timeStr = valueNode.textValue();
                LocalTime parsedLocalTime = null;
                try {
                    parsedLocalTime = LocalTime.parse(timeStr);
                } catch (IllegalArgumentException ex) {
                    // Swallow exception. We have better logging later in the chain.
                }

                if (parsedLocalTime == null) {
                    // If that doesn't work, fall back to parsing a full timestamp and use just the LocalTime part.
                    DateTime parsedDateTime = parseIosTimestamp(timeStr);
                    if (parsedDateTime != null) {
                        parsedLocalTime = parsedDateTime.toLocalTime();
                    }
                }

                if (parsedLocalTime != null) {
                    return CanonicalizationResult.makeResult(new TextNode(parsedLocalTime.toString()));
                } else {
                    return CanonicalizationResult.makeError("Invalid time string " + timeStr);
                }
            }
            case TIMESTAMP: {
                if (valueNode.isNumber()) {
                    // If this is a number, then it's epoch milliseconds (implicitly in UTC).
                    return CanonicalizationResult.makeResult(new TextNode(DateUtils.convertToISODateTime(
                            valueNode.longValue())));
                } else if (valueNode.isTextual()) {
                    String dateTimeStr = valueNode.textValue();
                    DateTime parsedDateTime = parseIosTimestamp(dateTimeStr);
                    if (parsedDateTime != null) {
                        return CanonicalizationResult.makeResult(new TextNode(parsedDateTime.toString()));
                    } else {
                        return CanonicalizationResult.makeError("Invalid date-time (timestamp) string " + dateTimeStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid date-time (timestamp) JSON value " +
                            valueNode.toString());
                }
            }
            default: {
                // Should never happen, but just in case.
                return CanonicalizationResult.makeError("Unknown field type " + type.name());
            }
        }
    }

    /**
     * Call this function to convert any JSON node into a string node. If the JSON node is already a string, return it
     * as is. If it's not a string, the returned node is a string with the JSON text as its value. For type-safety and
     * null safety, null values are returned as is.
     *
     * @param inputNode
     *         node to convert
     * @return converted node
     */
    public static JsonNode convertToStringNode(JsonNode inputNode) {
        if (inputNode == null || inputNode.isNull()) {
            // pass back nulls
            return inputNode;
        } else if (inputNode.isTextual()) {
            // This is already a string node. Note that this is identical to the null case, but we use a separate
            // else-if block for code organization. (The compiler will optimize it away anyway.)
            return inputNode;
        } else {
            return new TextNode(inputNode.toString());
        }
    }

    /**
     * Helper method to get the value of a JSON node as string. If the JSON node is a string type, it will return the
     * string value. Otherwise, it'll return the text representation of the JSON.
     *
     * @param node
     *         JSON node to convert to string
     * @return JSON node as string
     */
    public static String getAsString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.textValue();
        } else {
            return node.toString();
        }
    }

    // Helper method to test that two field defs are identical except for the maxAppVersion field (which can only be
    // added to newFieldDef). This is because adding maxAppVersion is how we mark fields as deprecated. Package-scoped
    // to facilitate unit tests.

    /**
     * Helper method to test whether a schema field definition can be modified as specified.
     *
     * @param oldFieldDef
     *         the original field definition, before modification
     * @param newFieldDef
     *         the new field definition, representing the intended modification
     * @return true if the fields can be modified, false otherwise
     */
    public static boolean isCompatibleFieldDef(UploadFieldDefinition oldFieldDef, UploadFieldDefinition newFieldDef) {
        // Short-cut: If they are equal, then they are compatible.
        if (oldFieldDef.equals(newFieldDef)) {
            return true;
        }

        // Attributes that don't affect field def compatibility
        // fileExtension - This is just a hint to BridgeEX for serializing the values. It doesn't affect the columns or
        //   validation.
        // mimeType - Similarly, this is also just a serialization hint.
        // min/maxAppVersion - These will be dummied out. TODO remove this comment when no longer needed

        // Different types are obviously not compatible.
        // This is the most likely reason fields are incompatible. Check this first.
        if (oldFieldDef.getType() != newFieldDef.getType()) {
            return false;
        }

        // allowOther - You can flip this to true (adds a field), but you can't flip it from true to false.
        Boolean oldAllowOther = oldFieldDef.getAllowOtherChoices();
        Boolean newAllowOther = newFieldDef.getAllowOtherChoices();
        if (oldAllowOther != null && oldAllowOther && (newAllowOther == null || !newAllowOther)) {
            return false;
        }

        // Changing the maxLength will cause the Synapse column to be recreated, so we need to block this. (Strictly
        // speaking, BridgeEX has code to prevent this by ignoring the new max length if it is different, for legacy
        // reasons but this is confusing behavior, so we should just prevent this situation from happening to begin
        // with.)
        if (!Objects.equals(oldFieldDef.getMaxLength(), newFieldDef.getMaxLength())) {
            return false;
        }

        List<String> oldMultiChoiceAnswerList = oldFieldDef.getMultiChoiceAnswerList();
        List<String> newMultiChoiceAnswerList = newFieldDef.getMultiChoiceAnswerList();
        if (oldMultiChoiceAnswerList != null && newMultiChoiceAnswerList != null) {
            // Choices might have been re-ordered, so convert to sets so we can determine the choices that have been
            // added, deleted, or retained.
            Set<String> oldMultiChoiceAnswerSet = new HashSet<>(oldMultiChoiceAnswerList);
            Set<String> newMultiChoiceAnswerSet = new HashSet<>(newMultiChoiceAnswerList);

            // Adding choices is okay. Deleting choices is not. (Renaming is deleting one choice and adding another.)
            Set<String> deletedChoiceSet = Sets.difference(oldMultiChoiceAnswerSet, newMultiChoiceAnswerSet);
            if (!deletedChoiceSet.isEmpty()) {
                return false;
            }
        } else if (oldMultiChoiceAnswerList != null || newMultiChoiceAnswerList != null) {
            // This should never happen, but if we add or remove a multi-choice answer list, we should flag the field
            // defs as incompatible.
            return false;
        }

        // This should never happen, but if for some reason, the field name changes, the fields are incompatible.
        if (!Objects.equals(oldFieldDef.getName(), newFieldDef.getName())) {
            return false;
        }

        // Going from required to optional is fine. Going from optional to required is okay only if you're adding a
        // minAppVerison.
        if (!oldFieldDef.isRequired() && newFieldDef.isRequired()) {
            return false;
        }

        // isUnboundedText controls whether we use a String or LargeText in Synapse. So changing this is not
        // compatible. (null defaults to false)
        //noinspection ConstantConditions
        boolean oldIsUnboundedText = oldFieldDef.isUnboundedText() != null ? oldFieldDef.isUnboundedText() : false;
        //noinspection ConstantConditions
        boolean newIsUnboundedText = newFieldDef.isUnboundedText() != null ? newFieldDef.isUnboundedText() : false;
        if (oldIsUnboundedText != newIsUnboundedText) {
            return false;
        }

        // If we passed all incompatibility checks, then we're compatible.
        return true;
    }

    /**
     * <p>
     * For some reason, the iOS are inserting arbitrary times into calendar dates. We need to convert them back to
     * calendar dates. This method simply detects if the string is too long and then truncates it into 10 characters,
     * then tries to parse the result.
     * </p>
     * <p>
     * Note that converting timestamps into calendar dates is inherently ambiguous, since a single timestamp can
     * represent two (or more) calendar dates. Example: 2015-12-23T00:00Z vs 2015-12-22T16:00-08:00
     * </p>
     *
     * @param dateStr
     *         string to parse into a calendar date
     * @return parsed LocalDate, or null if it couldn't be parsed
     */
    public static LocalDate parseIosCalendarDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        if (dateStr.length() > 10) {
            logger.warn("Non-standard calendar date in upload data: " + dateStr);
            dateStr = dateStr.substring(0, 10);
        }

        try {
            return DateUtils.parseCalendarDate(dateStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted calendar date in upload data: " + dateStr);
            return null;
        }
    }

    /**
     * For some reason, the iOS apps are sometimes sending timestamps in form "YYYY-MM-DD hh:mm:ss +ZZZZ", which is
     * non-ISO-compliant and can't be parsed by JodaTime. We'll need to convert these to ISO format, generally
     * "YYYY-MM-DDThh:mm:ss+ZZZZ".
     *
     * @param timestampStr
     *         raw timestamp string
     * @return parsed DateTime, or null if it couldn't be parsed
     */
    public static DateTime parseIosTimestamp(String timestampStr) {
        // Timestamps must have at least 11 chars to represent the date, at minimum.
        if (StringUtils.isBlank(timestampStr) || timestampStr.length() < 11) {
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            // Log something, so we can keep track of how often this happens.
            logger.warn("Non-standard timestamp in upload data: " + timestampStr);

            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted timestamp in upload data: " + timestampStr);
            return null;
        }
    }
}
