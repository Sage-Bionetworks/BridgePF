package org.sagebionetworks.bridge;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisCloseTest {

    public static void main(String[] args) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        String host = "localhost";
        int port = 6379;
        int timeout = 10;
        try (JedisPool jedisPool = new JedisPool(poolConfig, host, port, timeout)) {
            Jedis jedis = jedisPool.getResource();
            jedis.close();
            jedis.close();
        }
    }
}
