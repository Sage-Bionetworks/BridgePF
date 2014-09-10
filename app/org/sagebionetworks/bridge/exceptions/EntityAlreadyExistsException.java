package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class EntityAlreadyExistsException extends BridgeServiceException {
    private static final long serialVersionUID = 4048515680366593054L;

    private BridgeEntity entity;
    
    public EntityAlreadyExistsException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
    }
    
    public EntityAlreadyExistsException(BridgeEntity entity) {
        this(entity, entity.getClass().getSimpleName() + " already exists.");
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
