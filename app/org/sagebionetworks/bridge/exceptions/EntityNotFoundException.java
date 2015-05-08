package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
@NoStackTraceException
public class EntityNotFoundException extends BridgeServiceException {
    
    private Class<? extends BridgeEntity> clazz; 
    
    public EntityNotFoundException(Class<? extends BridgeEntity> clazz) {
        this(clazz, BridgeUtils.getTypeName(clazz) + " not found.");
    }
    
    public EntityNotFoundException(Class<? extends BridgeEntity> clazz, String message) {
        super(message, HttpStatus.SC_NOT_FOUND);
        this.clazz = clazz;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return clazz;
    }

}
