package org.sagebionetworks.bridge.exceptions;

@SuppressWarnings("serial")
public class EntityPublishedException extends BridgeServiceException {

    public EntityPublishedException(String message) {
        super(message, 400);
    }

}
