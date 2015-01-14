package org.sagebionetworks.bridge.models.upload;

/**
 * Represents field types in upload data. This is used for parsing the data from the upload buckets and to write the
 * data to the export data.
 */
public enum UploadFieldType {
    /** Raw data, generally a JSON map or JSON array */
    BLOB,

    /** A boolean, expected values match Boolean.parse() */
    BOOLEAN,

    /** A calendar date in YYYY-MM-DD format */
    CALENDAR_DATE,

    /** A floating point number, generally represented as a double in Java */
    FLOAT,

    /** An integer value, generally represented as a long in Java (64-bit signed integer) */
    INT,

    /** A string value */
    STRING,

    /** A timestamp in ISO 8601 format (YYYY-MM-DDThhhh:mm:ss+/-zz:zz) */
    TIMESTAMP,
}
