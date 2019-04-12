package org.sagebionetworks.bridge.upload;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.schema.SchemaUtils;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    private static final Logger logger = LoggerFactory.getLogger(UploadUtil.class);

    // Common field names
    public static final String FILENAME_INFO_JSON = "info.json";
    public static final String FILENAME_METADATA_JSON = "metadata.json";
    public static final String FIELD_ANSWERS = "answers";
    public static final String FIELD_APP_VERSION = "appVersion";
    public static final String FIELD_CREATED_ON = "createdOn";
    public static final String FIELD_DATA_FILENAME = "dataFilename";
    public static final String FIELD_FORMAT = "format";
    public static final String FIELD_ITEM = "item";
    public static final String FIELD_PHONE_INFO = "phoneInfo";
    public static final String FIELD_SCHEMA_REV = "schemaRevision";
    public static final String FIELD_SURVEY_GUID = "surveyGuid";
    public static final String FIELD_SURVEY_CREATED_ON = "surveyCreatedOn";
    public static final int FILE_SIZE_LIMIT_SURVEY_ANSWER = 3 * 1024;
    public static final int FILE_SIZE_LIMIT_INLINE_FIELD = 10 * 1024;
    public static final int FILE_SIZE_LIMIT_DATA_FILE = 2 * 1024 * 1024;
    public static final int WARNING_LIMIT_PARSED_JSON = 5 * 1024 * 1024;
    public static final int FILE_SIZE_LIMIT_PARSED_JSON = 20 * 1024 * 1024;

    // Regex patterns and strings for validation.
    private static final Pattern FIELD_NAME_MULTIPLE_SPECIAL_CHARS_PATTERN = Pattern.compile("[\\-\\._ ]{2,}");
    private static final Pattern FIELD_NAME_SPECIAL_CHARS_PATTERN = Pattern.compile("[\\-\\._ ]");
    private static final Pattern FIELD_NAME_VALID_CHARS_PATTERN = Pattern.compile("[a-zA-Z0-9\\-\\._ ]+");
    public static final String INVALID_ANSWER_CHOICE_ERROR_MESSAGE = "invalid value %s: must start and end with an " +
            "alphanumeric character, can only contain alphanumeric characters, spaces, dashes, underscores, and " +
            "periods, can't contain two or more non-alphanumeric characters in a row";
    public static final String INVALID_FIELD_NAME_ERROR_MESSAGE = INVALID_ANSWER_CHOICE_ERROR_MESSAGE +
            ", and can't be a reserved keyword";

    // Synapse allows column names of up to 256 characters. Synapse columns are created for each schema field, as well
    // as for each multiple choice answer. For multiple choice answers, we append the field name with the answer,
    // delimited by a '.'. If we set the max length to 127, this all but guarantees we will never have a Synapse column
    // name longer than 256 characters
    private static final int FIELD_NAME_MAX_LENGTH = 127;

    // Misc constants
    private static final int DEFAULT_MAX_LENGTH = 100;

    // Field def for survey schemas, which contains a key-value pair of all survey answers.
    public static final UploadFieldDefinition ANSWERS_FIELD_DEF = new UploadFieldDefinition.Builder()
            .withName(FIELD_ANSWERS).withRequired(true).withType(UploadFieldType.LARGE_TEXT_ATTACHMENT)
            .build();

    // Map of allowed field type changes. Key is the old type. Value is the new type.
    //
    // A few notes: This is largely based on whether the data can be converted in Synapse tables. Since booleans are
    // stored as 0/1 and dates are stored as epoch milliseconds, converting these to strings means old values will be
    // numeric types, but new values are likely to be "true"/"false" or ISO8601 timestamps. This leads to more
    // confusion overall, so we've decided to block it.
    //
    // Similarly, if you convert a bool to a numeric type (int, float), Synapse will convert the bools to 0s and 1s.
    // However, old bools in DynamoDB are still using "true"/"false", which will no longer serialize to Synapse. To
    // prevent this data loss, we're also not allowing bools to convert to numeric types.
    //
    // Additionally, multi-choice and timestamp fields create multiple columns in Synapse. Changing to single-column
    // types may be confusing, so we prevent these as well.
    private static final SetMultimap<UploadFieldType, UploadFieldType> ALLOWED_OLD_TYPE_TO_NEW_TYPE =
            ImmutableSetMultimap.<UploadFieldType, UploadFieldType>builder()
                    // All attachment types can be migrated to attachment_v2, but not the other way around.
                    .put(UploadFieldType.ATTACHMENT_BLOB, UploadFieldType.ATTACHMENT_V2)
                    .put(UploadFieldType.ATTACHMENT_CSV, UploadFieldType.ATTACHMENT_V2)
                    .put(UploadFieldType.ATTACHMENT_JSON_BLOB, UploadFieldType.ATTACHMENT_V2)
                    .put(UploadFieldType.ATTACHMENT_JSON_TABLE, UploadFieldType.ATTACHMENT_V2)
                    // Numeric types can changed to types with more precision (int to float), but not less
                    // precision (float to int).
                    .put(UploadFieldType.INT, UploadFieldType.FLOAT)
                    // inline_json_blob values are parseable as JSON. This precludes string types, since JSON can't
                    // parse unquoted strings. Numbers are fine.
                    .put(UploadFieldType.FLOAT, UploadFieldType.INLINE_JSON_BLOB)
                    .put(UploadFieldType.INT, UploadFieldType.INLINE_JSON_BLOB)
                    // Anything can be converted to string types (except for attachments, multi-choice, and
                    // timestamps), and single_choice is functionally equivalent to strings as far as data migration.
                    .put(UploadFieldType.CALENDAR_DATE, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.DURATION_V2, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.FLOAT, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.INLINE_JSON_BLOB, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.INT, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.STRING, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.TIME_V2, UploadFieldType.SINGLE_CHOICE)
                    .put(UploadFieldType.CALENDAR_DATE, UploadFieldType.STRING)
                    .put(UploadFieldType.DURATION_V2, UploadFieldType.STRING)
                    .put(UploadFieldType.FLOAT, UploadFieldType.STRING)
                    .put(UploadFieldType.INLINE_JSON_BLOB, UploadFieldType.STRING)
                    .put(UploadFieldType.INT, UploadFieldType.STRING)
                    .put(UploadFieldType.SINGLE_CHOICE, UploadFieldType.STRING)
                    .put(UploadFieldType.TIME_V2, UploadFieldType.STRING)
                    // The only thing that can be turned into a timestamp is an int, which can be epoch milliseconds.
                    .put(UploadFieldType.INT, UploadFieldType.TIMESTAMP)
                    .build();

    // When we determine if we're shrinking or growing fields, we use this to determine what the "length" of a field,
    // based on the field type. For any type not in this list, we use DEFAULT_MAX_LENGTH.
    private static final Map<UploadFieldType, Integer> MAX_LENGTH_BY_TYPE =
            ImmutableMap.<UploadFieldType, Integer>builder()
                    // YYYY-MM-DD
                    .put(UploadFieldType.CALENDAR_DATE, 10)
                    // See ISO8601 duration format
                    .put(UploadFieldType.DURATION_V2, 24)
                    // Empirically, the longest float in Synapse is 22 chars long
                    .put(UploadFieldType.FLOAT, 22)
                    // Synapse uses a bigint (signed long), which can be 20 chars long
                    .put(UploadFieldType.INT, 20)
                    // hh:mm:ss.sss
                    .put(UploadFieldType.TIME_V2, 12)
                    .build();

    // List of Synapse keywords that can't be used as field names.
    private static final Set<String> RESERVED_FIELD_NAME_LIST = ImmutableSet.of("row_etag", "row_id", "row_version");

    // Number of bytes a schema field takes up in Synapse. This doesn't contain all field types, as some field types
    // have variable size.
    private static final Map<UploadFieldType, Integer> SYNAPSE_BYTE_SIZE_BY_TYPE =
            ImmutableMap.<UploadFieldType, Integer>builder()
                    .put(UploadFieldType.ATTACHMENT_BLOB, 20)
                    .put(UploadFieldType.ATTACHMENT_CSV, 20)
                    .put(UploadFieldType.ATTACHMENT_JSON_BLOB, 20)
                    .put(UploadFieldType.ATTACHMENT_JSON_TABLE, 20)
                    .put(UploadFieldType.ATTACHMENT_V2, 20)
                    .put(UploadFieldType.BOOLEAN, 5)
                    // 10 chars for the calendar date, which is 30 bytes in Synapse
                    .put(UploadFieldType.CALENDAR_DATE, 30)
                    // 24 chars for duration, which is 72 bytes in Synapse
                    .put(UploadFieldType.DURATION_V2, 72)
                    .put(UploadFieldType.FLOAT, 23)
                    .put(UploadFieldType.INT, 20)
                    .put(UploadFieldType.LARGE_TEXT_ATTACHMENT, 3000)
                    // 12 chars for time, which is 36 bytes in Synapse
                    .put(UploadFieldType.TIME_V2, 36)
                    // Timestamp is a Synapse Date (20 bytes) + 5-char timezone (15 bytes)
                    .put(UploadFieldType.TIMESTAMP, 35)
                    .build();

    // LargeText (unbounded string) is always 3000 bytes in Synapse.
    private static final int SYNAPSE_LARGE_TEXT_BYTE_SIZE = 3000;

    // This set lists all the variable-length string types.
    private static final Set<UploadFieldType> VARIABLE_LENGTH_STRING_TYPE_SET = EnumSet.of(
            UploadFieldType.INLINE_JSON_BLOB, UploadFieldType.SINGLE_CHOICE, UploadFieldType.STRING);

    /*
     * Suffix used for unit fields in schemas. For example, if we had a field called "jogtime", we would have a field
     * called "jogtime_unit".
     */
    public static final String UNIT_FIELD_SUFFIX = "_unit";

    /** Calculates the total field size for the list of field definitions. */
    public static UploadFieldSize calculateFieldSize(List<UploadFieldDefinition> fieldDefList) {
        int numBytes = 0;
        int numColumns = 0;
        for (UploadFieldDefinition fieldDef : fieldDefList) {
            UploadFieldType fieldType = fieldDef.getType();
            if (fieldType == null) {
                // Since field size calculation happens as part of schema validation, it's possible that we might have
                // have invalid fields here. We can't calculate field size without an invalid field, so just skip this
                // field. The validator will throw anyway.
                continue;
            }

            // Calculate number of bytes.
            if (VARIABLE_LENGTH_STRING_TYPE_SET.contains(fieldType)) {
                // Special case for string types, since this scales based on max length.
                if (Boolean.TRUE.equals(fieldDef.isUnboundedText())) {
                    numBytes += SYNAPSE_LARGE_TEXT_BYTE_SIZE;
                } else {
                    // Synapse string fields cost 3 bytes per char.
                    int maxCharLength = fieldDef.getMaxLength() != null ? fieldDef.getMaxLength() : DEFAULT_MAX_LENGTH;
                    numBytes += 3*maxCharLength;
                }
            } else if (fieldType == UploadFieldType.MULTI_CHOICE) {
                // Multi-choice has a boolean column (5 bytes) for each answer. (Note that the field def builder
                // guarantees that the multi-choice answer list is not null, though it might be empty.
                numBytes += 5*fieldDef.getMultiChoiceAnswerList().size();

                // Multi-choice also adds a LargeText (3000 bytes) if allowOtherChoices is true.
                if (Boolean.TRUE.equals(fieldDef.getAllowOtherChoices())) {
                    numBytes += SYNAPSE_LARGE_TEXT_BYTE_SIZE;
                }
            } else if (SYNAPSE_BYTE_SIZE_BY_TYPE.containsKey(fieldType)) {
                numBytes += SYNAPSE_BYTE_SIZE_BY_TYPE.get(fieldType);
            } else {
                throw new BridgeServiceException("Couldn't get byte size for field type " + fieldType);
            }

            // Calculate number of columns.
            if (fieldType == UploadFieldType.MULTI_CHOICE) {
                // Multi-choice fields have a boolean column for each answer.
                if (fieldDef.getMultiChoiceAnswerList() != null) {
                    numColumns += fieldDef.getMultiChoiceAnswerList().size();
                }

                // Multi-choice also adds a LargeText if allowOtherChoices is true.
                if (Boolean.TRUE.equals(fieldDef.getAllowOtherChoices())) {
                    numColumns++;
                }
            } else if (fieldType == UploadFieldType.TIMESTAMP) {
                // Timestamp has two columns, one for epoch time, one for timezone.
                numColumns += 2;
            } else  {
                // Every other field type creates only one column.
                numColumns++;
            }
        }

        return new UploadFieldSize(numBytes, numColumns);
    }

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
            case INLINE_JSON_BLOB:
            case LARGE_TEXT_ATTACHMENT: {
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
        // required - This might break StrictValidation, but it doesn't impact the data in Synapse, so we should allow
        //   it.

        // If types are different, check the table.
        if (oldFieldDef.getType() != newFieldDef.getType()) {
            Set<UploadFieldType> allowedNewTypes = ALLOWED_OLD_TYPE_TO_NEW_TYPE.get(oldFieldDef
                    .getType());
            if (!allowedNewTypes.contains(newFieldDef.getType())) {
                return false;
            }
        }

        // allowOther - You can flip this to true (adds a field), but you can't flip it from true to false.
        Boolean oldAllowOther = oldFieldDef.getAllowOtherChoices();
        Boolean newAllowOther = newFieldDef.getAllowOtherChoices();
        if (oldAllowOther != null && oldAllowOther && (newAllowOther == null || !newAllowOther)) {
            return false;
        }

        // For string types, check max length. You can increase the max length, but you can't decrease it.
        if (UploadFieldType.STRING_TYPE_SET.contains(newFieldDef.getType()) &&
                !Objects.equals(oldFieldDef.getMaxLength(), newFieldDef.getMaxLength())) {
            int oldMaxLength;
            if (oldFieldDef.getMaxLength() != null) {
                // If the old field def specified a max length, just use it.
                oldMaxLength = oldFieldDef.getMaxLength();
            } else if (MAX_LENGTH_BY_TYPE.containsKey(oldFieldDef.getType())) {
                // The max length of the old field type is specified by its type.
                oldMaxLength = MAX_LENGTH_BY_TYPE.get(oldFieldDef.getType());
            } else {
                // It's probably a string type with the default length.
                oldMaxLength = DEFAULT_MAX_LENGTH;
            }

            // The new field is a string type. If the length isn't specified, it has the default max length.
            // If max lengths aren't specified, Bridge treats the max length as 100.
            int newMaxLength = (newFieldDef.getMaxLength() != null) ? newFieldDef.getMaxLength() : DEFAULT_MAX_LENGTH;

            // You can't decrease max length.
            if (newMaxLength < oldMaxLength) {
                return false;
            }
        }

        // Note: multiChoiceAnswerList can never be null.
        List<String> oldMultiChoiceAnswerList = oldFieldDef.getMultiChoiceAnswerList();
        List<String> newMultiChoiceAnswerList = newFieldDef.getMultiChoiceAnswerList();
        // Choices might have been re-ordered, so convert to sets so we can determine the choices that have been
        // added, deleted, or retained.
        Set<String> oldMultiChoiceAnswerSet = new HashSet<>(oldMultiChoiceAnswerList);
        Set<String> newMultiChoiceAnswerSet = new HashSet<>(newMultiChoiceAnswerList);

        // Adding choices is okay. Deleting choices is not. (Renaming is deleting one choice and adding another.)
        Set<String> deletedChoiceSet = Sets.difference(oldMultiChoiceAnswerSet, newMultiChoiceAnswerSet);
        if (!deletedChoiceSet.isEmpty()) {
            return false;
        }

        // This should never happen, but if for some reason, the field name changes, the fields are incompatible.
        if (!Objects.equals(oldFieldDef.getName(), newFieldDef.getName())) {
            return false;
        }

        // Converting from unbounded text may result in data loss, so it's not allowed.
        boolean oldIsUnboundedText = oldFieldDef.isUnboundedText() != null ? oldFieldDef.isUnboundedText() : false;
        boolean newIsUnboundedText = newFieldDef.isUnboundedText() != null ? newFieldDef.isUnboundedText() : false;
        if (oldIsUnboundedText && !newIsUnboundedText) {
            return false;
        }

        // If we passed all incompatibility checks, then we're compatible.
        return true;
    }

    /**
     * <p>
     * Validates a survey answer choice. Since these become Synapse table fields, we need to validate them. For
     * consistency, we want to apply the same rules to both single-choice and multi-choice.
     * </p>
     * <p>
     * Rules:
     * 1. must start and end with an alphanumeric character
     * 2. can only contain alphanumeric characters, spaces, dashes, underscores, and periods
     * 3. can't contain two or more non-alphanumeric characters in a row
     * 4. can't be longer than the max length
     * </p>
     *
     * @param name
     *         name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidAnswerChoice(String name) {
        // Blank names are invalid.
        if (StringUtils.isBlank(name)) {
            return false;
        }

        // Can only contain alphanumeric, space, dash, underscore, and period.
        if (!FIELD_NAME_VALID_CHARS_PATTERN.matcher(name).matches()) {
            return false;
        }

        // Must start and end with an alphanumeric char
        String firstChar = name.substring(0, 1);
        if (FIELD_NAME_SPECIAL_CHARS_PATTERN.matcher(firstChar).matches()) {
            return false;
        }

        int nameLength = name.length();
        String lastChar = name.substring(nameLength - 1, nameLength);
        if (FIELD_NAME_SPECIAL_CHARS_PATTERN.matcher(lastChar).matches()) {
            return false;
        }

        // Can't contain multiple special chars in a row.
        if (FIELD_NAME_MULTIPLE_SPECIAL_CHARS_PATTERN.matcher(name).find()) {
            return false;
        }

        // Can't be too long.
        if (name.length() > FIELD_NAME_MAX_LENGTH) {
            return false;
        }

        // Exhausted all our rules, so it must be valid.
        return true;
    }

    /**
     * Validates a schema field name or survey question name (identifier). This includes all the same rules as
     * {@link #isValidAnswerChoice}, and in addition, the name can't be a reserved keyword.
     *
     * @param name
     *         name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSchemaFieldName(String name) {
        // Valid schema field name follows all the same rules as a valid survey answer choice. (This also checks for
        // nulls and blanks and names that are too long.)
        if (!isValidAnswerChoice(name)) {
            return false;
        }

        // In addition, it can't be a reserved keyword. Reserved keywords are case-insensitive, so flatten the name to
        // lowercase.
        if (RESERVED_FIELD_NAME_LIST.contains(name.toLowerCase())) {
            return false;
        }

        // Exhausted all our rules, so it must be valid.
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

    /**
     * <p>
     * Sanitize the field names from the upload to match the rules for sanitizing field names in schemas. (This hasn't
     * yet become a problem in Prod, but we're adding the safeguards to ensure it never becomes a problem.)
     * </p>
     * <p>
     * Note: This does not modify the original map.
     * </p>
     *
     * @param rawFieldMap
     *         map whose keys need to be sanitizied
     * @param <T>
     *         generic type corresponding to the map's value type (generally JsonNode for JSON data or byte[] for
     *         non-JSON data)
     * @return the sanitized map
     */
    public static <T> Map<String, T> sanitizeFieldNames(Map<String, T> rawFieldMap) {
        Map<String, T> sanitizedFieldMap = new HashMap<>();
        for (Map.Entry<String, T> oneRawFieldEntry : rawFieldMap.entrySet()) {
            String rawFieldName = oneRawFieldEntry.getKey();
            String sanitizedFieldName = SchemaUtils.sanitizeFieldName(rawFieldName);
            sanitizedFieldMap.put(sanitizedFieldName, oneRawFieldEntry.getValue());
        }
        return sanitizedFieldMap;
    }
}
