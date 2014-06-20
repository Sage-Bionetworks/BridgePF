package org.sagebionetworks.bridge.redis;

/**
 * A Redis key concatenates the original key with a list of domains to form a compound key
 * where different parts are separated by ':'.
 * <p>
 * Examples:
 * <p>
 * <code>
 *    {id}:id:user
 *    {session}:session:user
 *    {email}:email:user
 * </code>
 * <p>
 * where keys are put into different "user" domains such as keys across domains are
 * allowed to have duplicates whereas within each domain they are unique.
 */
public interface RedisKey {

    /** For internal locking. */
    public static final RedisKey LOCK = new SimpleKey("lock");

    /** User sessions. */
    public static final RedisKey SESSION = new SimpleKey("session");

    /** User health code. */
    public static final RedisKey HEALTH_CODE = new SimpleKey("health-code");

    /** Health code lock. */
    public static final RedisKey HEALTH_CODE_LOCK = new CompoundKey((SimpleKey)HEALTH_CODE, (SimpleKey)LOCK);

    static final String SEPARATOR = ":";

    /**
     * The suffix that is appended to the original key to obtain the Redis key.
     */
    String getSuffix();

    /**
     * The compound Redis key.
     */
    String getRedisKey(String key);

    /**
     * The original key.
     */
    String getOriginalKey(String redisKey);
}
