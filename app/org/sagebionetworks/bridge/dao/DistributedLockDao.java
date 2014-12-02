package org.sagebionetworks.bridge.dao;

/**
 * Lock on an identifier for any specific class of system objects 
 * (thought to make this BridgeEntity but that's not used systematically
 * at this point).
 */
public interface DistributedLockDao {

    String acquire(Class<?> clazz, String identifier);

    String acquire(Class<?> clazz, String identifier, int expireInSeconds);

    boolean release(Class<?> clazz, String identifier, String lockId);
}
