package org.sagebionetworks.bridge.dao;

public class ConsentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 7966546401978600518L;

    public ConsentNotFoundException() {
        super("Must consent first.");
    }
}
