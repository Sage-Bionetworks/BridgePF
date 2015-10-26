package org.sagebionetworks.bridge.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
@NoStackTraceException
public class EntityAlreadyExistsException extends BridgeServiceException {

    private BridgeEntity entity;
    
    public EntityAlreadyExistsException(BridgeEntity entity) {
        this(entity, BridgeUtils.getTypeName(entity.getClass()) + " already exists.");
    }
    
    public EntityAlreadyExistsException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entity = checkNotNull(entity);
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
