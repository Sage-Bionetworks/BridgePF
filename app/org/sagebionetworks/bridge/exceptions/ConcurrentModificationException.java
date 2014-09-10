package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class ConcurrentModificationException extends BridgeServiceException {
    private static final long serialVersionUID = 282926281684175001L;
    
    private BridgeEntity entity;
    
    public ConcurrentModificationException(BridgeEntity entity) {
        super(entity.getClass().getSimpleName() + " has the wrong version number; it may have been saved in the background.", HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }
}
