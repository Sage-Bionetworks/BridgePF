package org.sagebionetworks.bridge.util;

@SuppressWarnings("serial")
public class ZipOverflowException extends RuntimeException {

    public ZipOverflowException(String message) {
        super(message);
    }
}
