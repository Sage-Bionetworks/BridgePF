package org.sagebionetworks.bridge.models.upload;

/** Represents the lifecycle of an upload object. */
public enum UploadStatus {
    /**
     * Upload status is unknown. This is generally used for older data formats (namely, Upload v1), which had a binary
     * complete flag rather than an UploadStatus.
     */
    UNKNOWN,

    /** Initial state. Upload is requested. User needs to upload to specified URL and call uploadComplete. */
    REQUESTED,

    /** User has called uploadComplete. Upload validation is currently taking place. */
    VALIDATION_IN_PROGRESS,

    /** Upload validation has failed. */
    VALIDATION_FAILED,

    /** Upload is a duplicate of an existing upload and was ignored. */
    DUPLICATE,

    /** Upload has succeeded, including validation. */
    SUCCEEDED,
}
