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
 * Note that keys are separated into different domains to avoid conflicts.
 */
public interface RedisKey {

    /** For internal locking. */
    RedisKey LOCK = new SimpleKey("lock");

    /** User sessions. */
    RedisKey SESSION = new SimpleKey("session");

    /** Study */
    RedisKey STUDY = new SimpleKey("study");

    /** User health code. */
    RedisKey HEALTH_CODE = new SimpleKey("health-code");

    /** User (email). */
    RedisKey USER = new SimpleKey("user");

    /** Health code lock. */
    RedisKey HEALTH_CODE_LOCK = new CompoundKey((SimpleKey)HEALTH_CODE, (SimpleKey)LOCK);

    /** Lock on user account. */
    RedisKey USER_LOCK = new CompoundKey((SimpleKey)USER, (SimpleKey)LOCK);

    /** Number of participants in a study */
    RedisKey NUM_OF_PARTICIPANTS = new SimpleKey("num-of-participants");

    /** A cached JSON response. */
    RedisKey VIEW = new SimpleKey("view");

    String SEPARATOR = ":";

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
