package org.sagebionetworks.bridge.redis;

import javax.annotation.Nonnull;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Responsible for providing template code such as closing resources.
 */
abstract class AbstractJedisTemplate<T> {

    private final JedisPool jedisPool;

    AbstractJedisTemplate(@Nonnull JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    T execute() {
        try (Jedis jedis = jedisPool.getResource()) {
            return execute(jedis);
        }
    }

    abstract T execute(final Jedis jedis);
}
