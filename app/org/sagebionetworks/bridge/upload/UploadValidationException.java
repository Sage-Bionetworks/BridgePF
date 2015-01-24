package org.sagebionetworks.bridge.upload;

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
