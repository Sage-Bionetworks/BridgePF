package org.sagebionetworks.bridge.redis;

import redis.clients.jedis.Jedis;

public class JedisLongOps implements LongOps {

    @Override
    public RedisOp<Long> increment(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.incr(key);
            }
        };
    }

    @Override
    public RedisOp<Long> decrement(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.decr(key);
            }
        };
    }

    @Override
    public RedisOp<String> set(final String key, final long value) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.set(key, Long.toString(value));
            }
        };
    }

    @Override
    public RedisOp<String> setex(final String key, final int seconds, final long value) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.setex(key, seconds, Long.toString(value));
            }
        };
    }
    
    @Override
    public RedisOp<Long> get(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                String value = jedis.get(key);
                return (value == null) ? null : Long.parseLong(value);
            }
        };
    }

    @Override
    public RedisOp<Long> delete(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.del(key);
            }
        };
    }
}
