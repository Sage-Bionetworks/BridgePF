package org.sagebionetworks.bridge.redis;

import java.util.Set;

import redis.clients.jedis.Jedis;

public class JedisStringOps implements StringOps {

    @Override
    public RedisOp<Long> expire(final String key, final int seconds) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.expire(key, seconds);
            }
        };
    }

    @Override
    public RedisOp<String> setex(final String key, final int seconds, final String value) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.setex(key, seconds, value);
            }
        };
    }

    @Override
    public RedisOp<Long> setnx(final String key, final String value) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.setnx(key, value);
            }
        };
    }

    @Override
    public RedisOp<String> get(final String key) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.get(key);
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

    @Override
    public RedisOp<Long> ttl(final String key) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                return jedis.ttl(key);
            }
        };
    }

    public RedisOp<Long> clearRedis(final String keyPattern) {
        return new AbstractJedisTemplate<Long>() {
            @Override
            Long execute(Jedis jedis) {
                Set<String> keys = jedis.keys(keyPattern);
                for (String key : keys) {
                    jedis.del(key);
                }
                return new Long(keys.size());
            }
        };
    }

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
}
