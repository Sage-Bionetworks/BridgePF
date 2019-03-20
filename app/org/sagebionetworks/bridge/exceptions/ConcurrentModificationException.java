package org.sagebionetworks.bridge.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
@NoStackTraceException
public class ConcurrentModificationException extends BridgeServiceException {
    
    private BridgeEntity entity;
    
    public ConcurrentModificationException(BridgeEntity entity) {
        super(BridgeUtils.getTypeName(entity.getClass()) + " has the wrong version number; it may have been saved in the background.", HttpStatus.SC_CONFLICT);
        this.entity = checkNotNull(entity);
    }
    
    public ConcurrentModificationException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entity = checkNotNull(entity);
    }
    
    public ConcurrentModificationException(String message) {
        super(message, HttpStatus.SC_CONFLICT);
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity != null ? entity.getClass() : null;
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }
}
