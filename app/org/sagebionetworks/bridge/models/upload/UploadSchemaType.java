package org.sagebionetworks.bridge.models.upload;

/**
 * Tags schemas with the schema type, so we can figure out how to parse the data into the specified fields. Currently,
 * this only contains iOS-specific schema types. As we define new data formats, we will need to expand this enum. For
 * more information, see https://sagebionetworks.jira.com/wiki/display/BRIDGE/Bridge+Upload+Data+Format
 */
public enum UploadSchemaType {
    /** iOS data upload. For example, Parkinson's Tapping Activity. */
    IOS_DATA,

    /** iOS survey. For example, Asthma Weekly Prompt. */
    IOS_SURVEY,
}
