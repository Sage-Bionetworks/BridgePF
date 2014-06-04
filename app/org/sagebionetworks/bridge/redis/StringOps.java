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
}
