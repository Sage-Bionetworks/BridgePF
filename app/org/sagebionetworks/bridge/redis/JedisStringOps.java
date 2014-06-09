package org.sagebionetworks.bridge.redis;

import redis.clients.jedis.Jedis;

public class JedisStringOps implements StringOps {

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
    public RedisOp<String> get(final String key) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                return jedis.get(key);
            }
        };
    }
    
    @Override
    public RedisOp<String> delete(final String key) {
        return new AbstractJedisTemplate<String>() {
            @Override
            String execute(Jedis jedis) {
                Long keysRemoved = jedis.del(key);
                return (keysRemoved > 0L) ? "OK" : null;
            }
        };
    }
}
