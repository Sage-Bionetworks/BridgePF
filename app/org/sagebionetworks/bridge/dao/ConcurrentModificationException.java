package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class ConcurrentModificationException extends BridgeServiceException {

    private static final long serialVersionUID = 282926281684175001L;

    public ConcurrentModificationException() {
        super("The object version is incorrect; the object may have been saved in the background.", 400);
    }

}
