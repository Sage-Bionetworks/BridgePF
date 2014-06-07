package org.sagebionetworks.bridge.redis;

/**
 * For Redis strings (simple key-value pairs).
 */
public interface StringOps {

    /**
     * Sets the value of the key and makes it expire after the specified seconds.
     */
    RedisOp<String> setex(String key, int seconds, String value);

    /**
     * Gets the value of the specified key. If the key does not exist null is returned.
     */
    RedisOp<String> get(String key);
    
    /**
     * Deletes the value of the specified key. 
     * @param key
     * @return OK" if at least one key is deleted, or null if the key does not exist.
     */
    RedisOp<String> delete(String key);
}
