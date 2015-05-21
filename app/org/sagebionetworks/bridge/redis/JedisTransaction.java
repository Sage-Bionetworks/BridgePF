package org.sagebionetworks.bridge.redis;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Provides abstraction over Jedis's transactions.
 */
public final class JedisTransaction implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(JedisTransaction.class);

    private final Jedis jedis;
    private final Transaction transaction;

    JedisTransaction(Jedis jedis) {
        this.jedis = jedis;
        this.transaction = jedis.multi();
    }

    public JedisTransaction setex(final String key, final int seconds, final String value) {
        transaction.setex(key, seconds, value);
        return this;
    }

    public JedisTransaction expire(final String key, final int seconds) {
        transaction.expire(key, seconds);
        return this;
    }

    public JedisTransaction del(final String key) {
        transaction.del(key);
        return this;
    }

    public List<Object> exec() {
        try (Jedis jedis = this.jedis) {
            return transaction.exec();
        }
    }

    public String discard() {
        try (Jedis jedis = this.jedis) {
            return transaction.discard();
        }
    }

    @Override
    public void finalize() {
        close();
    }

    @Override
    public void close() {
        try {
            jedis.close();
        } catch (JedisException e) {
            // Jedis throws an exception here on closed connections
            // See https://github.com/xetorthio/jedis/issues/992
            logger.debug(e.getMessage(), e);
        }
    }
}
