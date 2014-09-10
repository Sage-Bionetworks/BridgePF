package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class InvalidEntityException extends BridgeServiceException {
    private static final long serialVersionUID = 8206007689868153093L;

    private BridgeEntity entity;
    
    public InvalidEntityException(BridgeEntity entity) {
        this(entity, entity.getClass().getSimpleName() + " is not valid.");
    }
    
    public InvalidEntityException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
