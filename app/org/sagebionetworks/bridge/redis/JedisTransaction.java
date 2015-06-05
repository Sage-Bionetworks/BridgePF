package org.sagebionetworks.bridge.redis;

import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * Provides abstraction over Jedis's transactions.
 */
public class JedisTransaction implements AutoCloseable {

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
        try {
            return transaction.exec();
        } finally {
            close();
        }
    }

    public String discard() {
        try {
            return transaction.discard();
        } finally {
            close();
        }
    }

    @Override
    public void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        JedisUtil.closeJedisConnection(jedis);
    }
}
