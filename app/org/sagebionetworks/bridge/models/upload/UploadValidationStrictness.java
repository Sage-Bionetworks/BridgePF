package org.sagebionetworks.bridge.models.upload;

/** Enumeration of upload validation strictness settings. */
public enum UploadValidationStrictness {
    /** The default setting, which is to log a warning into the Bridge Server logs. */
    WARNING,

    /**
     * In addition to logging a warning, this writes a validation message back into the health data record and exports
     * that message to Synapse.
     */
    REPORT,

    /** Throws an error if validation fails. */
    STRICT,
}
