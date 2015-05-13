package org.sagebionetworks.bridge.util;

@SuppressWarnings("serial")
public class DuplicateZipEntryException extends Exception {

    public DuplicateZipEntryException(String message) {
        super(message);
    }
}
