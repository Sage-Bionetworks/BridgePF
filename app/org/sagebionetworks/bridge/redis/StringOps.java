package org.sagebionetworks.bridge.redis;

/**
 * For Redis strings (simple key-value pairs).
 */
public interface StringOps {

    /**
     * The specified key will expire after seconds.
     * 
     * @param key
     *            target key.
     * @param seconds
     *            number of seconds until expiration.
     * @return
     */
    RedisOp<String> expire(String key, int seconds);

    /**
     * Sets the value of the key and makes it expire after the specified
     * seconds.
     * 
     * @param key
     *            key of the key-value pair.
     * @param seconds
     *            number of seconds until expiration.
     * @param value
     *            value of the key-value pair.
     */
    RedisOp<String> setex(String key, int seconds, String value);

    /**
     * Sets the value of the key if and only if the key does not already have a
     * value.
     * 
     * @param key
     *            key of the key-value pair.
     * @param value
     *            value of the key-value pair.
     * @return
     */
    RedisOp<String> setnx(String key, String value);

    /**
     * Gets the value of the specified key. If the key does not exist null is
     * returned.
     */
    RedisOp<String> get(String key);

    /**
     * Deletes the value of the specified key.
     * 
     * @param key
     *            key of the key-value pair
     * @return "OK" if at least one key is deleted, or null if the key does not
     *         exist.
     */
    RedisOp<String> delete(String key);

    /**
     * Determines the time until expiration for a key (time-to-live).
     * 
     * @param key
     *            key of the key-value pair.
     * @return ttl if key's expiration is set, and null if key doesn't exist or no
     *         expiration is set.
     */
    RedisOp<String> ttl(String key);

}
