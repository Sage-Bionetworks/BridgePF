package org.sagebionetworks.bridge.redis;

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
    public RedisOp<String> ttl(final String key) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                Long ttl = jedis.ttl(key);
                return (ttl > 0) ? Long.toString(ttl) : null;
            }
        };
    }
}
