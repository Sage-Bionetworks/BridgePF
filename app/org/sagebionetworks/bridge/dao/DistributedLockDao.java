package org.sagebionetworks.bridge.dao;

/**
 * Lock on an identifier for any specific class of system objects 
 * (thought to make this BridgeEntity but that's not used systematically
 * at this point).
 */
public interface DistributedLockDao {

    public String createLock(Class<?> clazz, String identifier);

    public void releaseLock(Class<?> clazz, String identifier, String lockId);

    public boolean isLocked(Class<?> clazz, String identifier);

}
