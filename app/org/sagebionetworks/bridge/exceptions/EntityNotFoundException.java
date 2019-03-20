package org.sagebionetworks.bridge.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
@NoStackTraceException
public class EntityNotFoundException extends BridgeServiceException {
    
    private Class<? extends BridgeEntity> entity; 
    
    public EntityNotFoundException(Class<? extends BridgeEntity> entity) {
        this(entity, BridgeUtils.getTypeName(entity) + " not found.");
    }
    
    public EntityNotFoundException(Class<? extends BridgeEntity> entity, String message) {
        super(message, HttpStatus.SC_NOT_FOUND);
        this.entity = checkNotNull(entity);
    }
    
    public String getEntityClass() {
        return BridgeUtils.getTypeName(entity);
    }

}
