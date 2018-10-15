package org.sagebionetworks.bridge.exceptions;

public class EntityPublishedException extends BridgeServiceException {

    public EntityPublishedException(String message) {
        super(message, 400);
    }

}
