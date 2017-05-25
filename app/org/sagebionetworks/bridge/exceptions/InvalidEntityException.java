package org.sagebionetworks.bridge.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.models.BridgeEntity;

@NoStackTraceException
@SuppressWarnings("serial")
public class InvalidEntityException extends BridgeServiceException {

    private BridgeEntity entity;
    private Map<String,List<String>> errors;
    
    public InvalidEntityException(BridgeEntity entity, String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = checkNotNull(entity);
    }
    
    public InvalidEntityException(BridgeEntity entity, String message, Map<String,List<String>> errors) {
        super(message, HttpStatus.SC_BAD_REQUEST);
        this.entity = checkNotNull(entity);
        this.errors = errors;
    }
    
    public InvalidEntityException(String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
    }
    
    public Map<String,List<String>> getErrors() {
        return errors;
    }

    public BridgeEntity getEntity() {
        return entity;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((entity == null) ? 0 : entity.hashCode());
        result = prime * result + ((errors == null) ? 0 : errors.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InvalidEntityException other = (InvalidEntityException) obj;
        if (entity == null) {
            if (other.entity != null)
                return false;
        } else if (!entity.equals(other.entity))
            return false;
        if (errors == null) {
            if (other.errors != null)
                return false;
        } else if (!errors.equals(other.errors))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "InvalidEntityException [message=" + getMessage() + ", entity=" + entity + ", errors=" + errors + "]";
    }

}
