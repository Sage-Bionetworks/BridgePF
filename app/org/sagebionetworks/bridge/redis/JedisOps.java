package org.sagebionetworks.bridge.redis;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Redis commands via Jedis.
 */
@Component
public class JedisOps {

    private final JedisPool jedisPool;

    @Autowired
    public JedisOps(@Nonnull JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * The specified key will expire after seconds.
     *
     * @param key
     *            target key.
     * @param seconds
     *            number of seconds until expiration.
     * @return success code
     *          1 if successful, 0 if key doesn't exist or timeout could not be set
     */
    public Long expire(final String key, final int seconds) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.expire(key, seconds);
            }
        }.execute();
    }

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
    public String setex(final String key, final int seconds, final String value) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.setex(key, seconds, value);
            }
        }.execute();
    }

    /**
     * Sets the value of the key if and only if the key does not already have a
     * value.
     *
     * @param key
     *            key of the key-value pair.
     * @param value
     *            value of the key-value pair.
     * @return success code
     *          1 if the key was set, 0 if not
     */
    public Long setnx(final String key, final String value) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.setnx(key, value);
            }
        }.execute();
    }

    /**
     * Gets the value of the specified key. If the key does not exist null is
     * returned.
     */
    public String get(final String key) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.get(key);
            }
        }.execute();
    }

    /**
     * Deletes the specified list of keys.
     *
     * @param keys
     *            the list of keys to be deleted.
     * @return number of keys deleted
     */
    public Long del(final String... keys) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.del(keys);
            }
        }.execute();
    }

    /**
     * Determines the time until expiration for a key (time-to-live).
     *
     * @param key
     *            key of the key-value pair.
     * @return ttl
     *      positive value if ttl is set, zero if not, negative if there was an error
     */
    public Long ttl(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.ttl(key);
            }
        }.execute();
    }

    /**
     * Increment the value by one
     * @param key
     * @return the new value of the key (after incrementing).
     */
    public Long incr(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.incr(key);
            }
        }.execute();
    }

    /**
     * Decrement the value by one.
     * @param key
     * @return the new value of the key (after decrementing).
     */
    public Long decr(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.decr(key);
            }
        }.execute();
    }

    /**
     * Responsible for providing template code such as closing resources.
     */
    private abstract class AbstractJedisTemplate<T> {

        T execute() {
            try (Jedis jedis = jedisPool.getResource()) {
                return execute(jedis);
            }
        }

        abstract T execute(final Jedis jedis);
    }
}
