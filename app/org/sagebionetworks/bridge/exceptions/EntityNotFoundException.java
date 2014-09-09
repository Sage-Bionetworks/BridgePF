package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class EntityNotFoundException extends BridgeServiceException {
    private static final long serialVersionUID = 884196407390465867L;
    
    private Class<? extends BridgeEntity> clazz; 
    
    public EntityNotFoundException(Class<? extends BridgeEntity> clazz) {
        this(clazz, clazz.getSimpleName() + " not found.");
    }
    
    public EntityNotFoundException(Class<? extends BridgeEntity> clazz, String message) {
        super(message, HttpStatus.SC_NOT_FOUND);
        this.clazz = clazz;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return clazz;
    }

}
