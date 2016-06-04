package org.sagebionetworks.bridge.upload;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    private static final Logger logger = LoggerFactory.getLogger(UploadUtil.class);

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
                    convertedValueNode.add(convertToStringNode(valueNode.get(i)));
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
