package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
public class ConcurrentModificationException extends BridgeServiceException {
    
    private BridgeEntity entity;
    
    public ConcurrentModificationException(BridgeEntity entity) {
        super(BridgeUtils.getTypeName(entity.getClass()) + " has the wrong version number; it may have been saved in the background.", HttpStatus.SC_CONFLICT);
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
