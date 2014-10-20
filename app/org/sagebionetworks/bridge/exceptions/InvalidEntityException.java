package org.sagebionetworks.bridge.exceptions;

import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class InvalidEntityException extends BridgeServiceException {
    private static final long serialVersionUID = 8206007689868153093L;

    private BridgeEntity entity;
    private Map<String,List<String>> errors;
    
    public InvalidEntityException(BridgeEntity entity) {
        this(entity, entity.getClass().getSimpleName() + " is not valid.");
    }
    
    public InvalidEntityException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
    }
    
    public InvalidEntityException(BridgeEntity entity, String message, Map<String,List<String>> errors) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = entity;
        this.errors = errors;
    }
    
    public InvalidEntityException(String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
    }
    
    public Map<String,List<String>> getErrors() {
        return errors;
    }
    
    public Class<? extends BridgeEntity> getEntityClass() {
        return entity.getClass();
    }
    
    public BridgeEntity getEntity() {
        return entity;
    }

}
