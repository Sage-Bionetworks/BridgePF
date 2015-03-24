package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@NoStackTraceException
public class EntityAlreadyExistsException extends BridgeServiceException {
    private static final long serialVersionUID = 4048515680366593054L;

    private BridgeEntity entity;
    
    public EntityAlreadyExistsException(BridgeEntity entity) {
        this(entity, BridgeUtils.getTypeName(entity.getClass()) + " already exists.");
    }
    
    public EntityAlreadyExistsException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entity = entity;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
