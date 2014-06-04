package org.sagebionetworks.bridge.redis;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

abstract class AbstractJedisTemplate<T> implements RedisOp<T> {

    private static final JedisPool JEDIS_POOL;
    static {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String host = config.getProperty("redis.host");
        int port = config.getPropertyAsInt("redis.port");
        int timeout = config.getPropertyAsInt("redis.timeout");
        String password = config.getProperty("redis.password");
        JEDIS_POOL = new JedisPool(poolConfig, host, port, timeout, password);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                JEDIS_POOL.destroy();
            }
        }));
    }

    @Override
    public T execute() {
        Jedis jedis = JEDIS_POOL.getResource();
        try {
            return execute(jedis);
        } catch (JedisConnectionException e) {
            if (jedis != null) {
                JEDIS_POOL.returnBrokenResource(jedis);
                jedis = null;
            }
            return null;
        } finally {
            if (jedis != null) {
                JEDIS_POOL.returnResource(jedis);
            }
        }
    }

    abstract T execute(Jedis jedis);
}
