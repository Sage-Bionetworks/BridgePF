package org.sagebionetworks.bridge.dao;

public class ConsentAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = -749583101970069021L;

    public ConsentAlreadyExistsException() {
        super("Already consented.");
    }
}
