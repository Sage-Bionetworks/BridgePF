package org.sagebionetworks.bridge.exceptions;

import java.util.Map;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@SuppressWarnings("serial")
@NoStackTraceException
public class EntityAlreadyExistsException extends BridgeServiceException {

    private final Map<String,Object> entityKeys;
    private final Class<? extends BridgeEntity> entityClass;
    
    public EntityAlreadyExistsException(Class<? extends BridgeEntity> entityClass, String message, Map<String,Object> entityKeys) {
        super(message, HttpStatus.SC_CONFLICT);
        this.entityClass = entityClass;
        this.entityKeys = (entityKeys == null) ? Maps.newHashMap() : entityKeys;
    }
    
    public EntityAlreadyExistsException(Class<? extends BridgeEntity> entityClass, Map<String,Object> entityKeys) {
        this(entityClass, BridgeUtils.getTypeName(entityClass) + " already exists.", entityKeys);
    }
    
    public EntityAlreadyExistsException(Class<? extends BridgeEntity> entityClass, String entityKey, String entityValue) {
        this(entityClass, new ImmutableMap.Builder<String,Object>().put(entityKey, entityValue).build());
    }
    
    public String getEntityClass() {
        return BridgeUtils.getTypeName(entityClass);
    }
    
    /**
     * These are the keys that are conflicting and indicate an entity already exists in the system. However, for users, where 
     * credentials can conflict and they are sensitive information, we use the account's ID instead (even if it's something like
     * an email address that has already been used).
     */
    public Map<String, Object> getEntityKeys() {
        return entityKeys;
    }
    
    /**
     * This originally returned a JSONified version of the entity, but will now return just the keys. No known case
     * where this is currently being used.
     */
    public Map<String, Object> getEntity() {
        return entityKeys;
    }
}
