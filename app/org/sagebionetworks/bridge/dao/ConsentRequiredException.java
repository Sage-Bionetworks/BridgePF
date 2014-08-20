package org.sagebionetworks.bridge.dao;

public class ConsentRequiredException extends RuntimeException {

    private static final long serialVersionUID = 7966546401978600518L;

    public ConsentRequiredException() {
        super("Must consent first.");
    }
}
