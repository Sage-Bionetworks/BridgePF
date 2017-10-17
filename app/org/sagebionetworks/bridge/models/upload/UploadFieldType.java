package org.sagebionetworks.bridge.models.upload;

import java.util.EnumSet;
import java.util.Set;

/**
 * Represents field types in upload data. This is used for parsing the data from the upload buckets and to write the
 * data to the export data.
 */
public enum UploadFieldType {
    /**
     * Health Data Attachment as a non-JSON blob. The value of this field is a foreign key into the Health Data
     * Attachments table. Data stored in this format will not be subject to additional post-processing.
     */
    ATTACHMENT_BLOB,

    /**
     * Health Data Attachment as a CSV file. The value of this field is a foreign key into the Health Data Attachments
     * table. We originally planned to de-normalize this data, but that feature was punted.
     */
    ATTACHMENT_CSV,

    /**
     * Health Data Attachment as a JSON blob. The value of this field is a foreign key into the Health Data Attachments
     * table. Data stored in this format will not be subject to additional post-processing, but is tagged as JSON data
     * in the schema for convenience.
     */
    ATTACHMENT_JSON_BLOB,

    /**
     * Health Data Attachment as a JSON blob of a specific "table" format. The value of this field is a foreign key
     * into the Health Data Attachments table. We originally planned to de-normalize this data, but that feature was
     * punted.
     */
    ATTACHMENT_JSON_TABLE,

    /** Upload V2 version of attachments. Fields of this type can be associated with any file extension or MIME type */
    ATTACHMENT_V2,

    /** A boolean, expected values match Boolean.parse() */
    BOOLEAN,

    /** A calendar date in YYYY-MM-DD format */
    CALENDAR_DATE,

    /** Duration (how long something took). Used by Upload v2. */
    DURATION_V2,

    /** A floating point number, generally represented as a double in Java */
    FLOAT,

    /**
     * A JSON blob that's small enough to fit in the health data. (Generally something that's only a few hundred
     * characters at most.
     */
    INLINE_JSON_BLOB,

    /** An integer value, generally represented as a long in Java (64-bit signed integer) */
    INT,

    /**
     * A value that is written to Synapse as a LargeText (unbounded String), but stored in Bridge as an attachment.
     * This allows us to have inline strings larger than ~10kb without hitting DynamoDB's 400kb record limit. This is
     * frequently used to upload large JSON blobs, like accelerometer data.
     */
    LARGE_TEXT_ATTACHMENT,

    /** Multiple choice question with multiple answers. */
    MULTI_CHOICE,

    /** Multiple choice question with only a single answer. */
    SINGLE_CHOICE,

    /** A string value */
    STRING,

    /** Time without date or timezone. Used in Upload v2. */
    TIME_V2,

    /** A timestamp in ISO 8601 format (YYYY-MM-DDThhhh:mm:ss+/-zz:zz) */
    TIMESTAMP;

    /** A set of upload field types that are considered attachment types. */
    public static final Set<UploadFieldType> ATTACHMENT_TYPE_SET = EnumSet.of(ATTACHMENT_BLOB, ATTACHMENT_CSV,
            ATTACHMENT_JSON_BLOB, ATTACHMENT_JSON_TABLE, ATTACHMENT_V2, LARGE_TEXT_ATTACHMENT);

    /**
     * A set of upload field types that are freeform (or mostly freeform) strings. This is used to make calculations
     * related to max length. As such, this doesn't include attachment types.
     */
    public static final Set<UploadFieldType> STRING_TYPE_SET = EnumSet.of(INLINE_JSON_BLOB, SINGLE_CHOICE, STRING);
}
