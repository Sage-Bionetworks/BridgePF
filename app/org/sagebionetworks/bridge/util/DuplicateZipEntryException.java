package org.sagebionetworks.bridge.util;

@SuppressWarnings("serial")
public class DuplicateZipEntryException extends RuntimeException {

    public DuplicateZipEntryException() {
        super();
    }

    public DuplicateZipEntryException(String message) {
        super(message);
    }
}
