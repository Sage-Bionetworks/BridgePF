package org.sagebionetworks.bridge.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.apache.http.HttpStatus;

import com.google.common.collect.ImmutableMap;

/**
 * For other data store constraint issues besides concurrent modification (most notably, foreign 
 * key constraint violations). The exception only provides information about the first object that 
 * references the entity that cannot be changed. As a general rule, provide the "type" of the 
 * entity as we expose it in the API for both the entity and the referrer, so callers can trace 
 * back the entities involved when there are multiple calls being made.
 */
@SuppressWarnings("serial")
@NoStackTraceException
public class ConstraintViolationException extends BridgeServiceException {

    private final Map<String,String> entityKeys;
    private final Map<String,String> referrerKeys;
    
    ConstraintViolationException(String message, Map<String, String> entityKeys,
            Map<String, String> referrerKeys) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entityKeys = entityKeys;
        this.referrerKeys = referrerKeys;
    }
    
    /**
     * The primary keys of the entity that is being manipulated.
     */
    public Map<String,String> getEntityKeys() {
        return entityKeys;
    }
    /**
     * The primary keys of the entity that prevents this entity from being manipulated.
     */
    public Map<String,String> getReferrerKeys() {
        return referrerKeys;
    }

    public static class Builder {
        private String message;
        private ImmutableMap.Builder<String,String> entityMapBuilder = new ImmutableMap.Builder<>();
        private ImmutableMap.Builder<String,String> referrerMapBuilder = new ImmutableMap.Builder<>();
        
        public Builder withEntityKey(String key, String value) {
            checkNotNull(key);
            checkNotNull(value);
            entityMapBuilder.put(key, value);
            return this;
        }
        public Builder withReferrerKey(String key, String value) {
            checkNotNull(key);
            checkNotNull(value);
            referrerMapBuilder.put(key, value);
            return this;
        }
        public Builder withMessage(String message) {
            checkNotNull(message);
            this.message = message;
            return this;
        }
        public ConstraintViolationException build() {
            Map<String,String> entityMap = entityMapBuilder.build();
            Map<String,String> referrerMap = referrerMapBuilder.build();
            if (message == null) {
                message = String.format("Operation not permitted because entity %s refers to this entity %s.",
                        referrerMap, entityMap);
            }
            return new ConstraintViolationException(message, entityMap, referrerMap);
        }
    }
}
