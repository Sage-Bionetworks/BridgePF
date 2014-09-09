package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class EntityAlreadyExistsException extends BridgeServiceException {
    private static final long serialVersionUID = 4048515680366593054L;

    private BridgeEntity entity;
    
    public EntityAlreadyExistsException(BridgeEntity entity) {
        super(entity.getClass().getSimpleName() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
