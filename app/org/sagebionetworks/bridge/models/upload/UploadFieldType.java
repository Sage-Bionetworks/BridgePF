package org.sagebionetworks.bridge.models.upload;

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
     * table. Data stored in this format will be used to construct de-normalized tables during data export.
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
     * into the Health Data Attachments table. Data stored in this format will be used to construct de-normalizeds
     * tables during data export.
     */
    // TODO: document ATTACHMENT_JSON_TABLE data format
    ATTACHMENT_JSON_TABLE,

    /** A boolean, expected values match Boolean.parse() */
    BOOLEAN,

    /** A calendar date in YYYY-MM-DD format */
    CALENDAR_DATE,

    /** A floating point number, generally represented as a double in Java */
    FLOAT,

    /**
     * A JSON blob that's small enough to fit in the health data. (Generally something that's only a few hundred
     * characters at most.
     */
    INLINE_JSON_BLOB,

    /** An integer value, generally represented as a long in Java (64-bit signed integer) */
    INT,

    /** A string value */
    STRING,

    /** A timestamp in ISO 8601 format (YYYY-MM-DDThhhh:mm:ss+/-zz:zz) */
    TIMESTAMP,
}
