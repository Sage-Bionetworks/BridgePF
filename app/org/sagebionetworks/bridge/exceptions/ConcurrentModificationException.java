package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class ConcurrentModificationException extends BridgeServiceException {
    private static final long serialVersionUID = 282926281684175001L;
    
    private BridgeEntity entity;
    
    public ConcurrentModificationException(BridgeEntity entity) {
        super(entity.getClass().getSimpleName() + " has the wrong version number; it may have been saved in the background.", HttpStatus.SC_CONFLICT);
        this.entity = entity;
    }
    
    public ConcurrentModificationException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entity = entity;
    }
    
    public ConcurrentModificationException(String message) {
        super(message, HttpStatus.SC_CONFLICT);
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }
}
