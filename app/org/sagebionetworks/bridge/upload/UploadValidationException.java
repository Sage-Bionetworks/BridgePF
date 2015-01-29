package org.sagebionetworks.bridge.upload;

/**
 * This exception represents an error in validating an upload. This is primarily used to wrap checked exceptions to
 * keep the interface of {@link org.sagebionetworks.bridge.upload.UploadValidationHandler} clean.
 */
@SuppressWarnings("serial")
public class UploadValidationException extends Exception {
    public UploadValidationException() {
    }

    public UploadValidationException(String message) {
        super(message);
    }

    public UploadValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public UploadValidationException(Throwable cause) {
        super(cause);
    }
}
