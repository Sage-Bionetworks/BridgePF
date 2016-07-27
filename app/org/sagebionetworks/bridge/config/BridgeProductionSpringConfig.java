package org.sagebionetworks.bridge.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import org.sagebionetworks.bridge.redis.JedisOps;

/**
 * Production-only Spring config. This includes things that we don't want in our unit tests for
 * reliability/repeatability concerns, most notably Redis
 */
@Configuration
public class BridgeProductionSpringConfig {
    private static Logger LOG = LoggerFactory.getLogger(BridgeProductionSpringConfig.class);

    @Autowired
    BridgeConfig bridgeConfig;
    
    @Resource(name = "redisProviders")
    List<String> redisProviders;
    
    @Bean(name = "jedisOps")
    @Resource(name = "jedisPool")
    public JedisOps jedisOps(final JedisPool jedisPool) {
        return new JedisOps(jedisPool);
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool() throws Exception {
        // Configure pool
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(bridgeConfig.getPropertyAsInt("redis.max.total"));
        poolConfig.setMinIdle(bridgeConfig.getPropertyAsInt("redis.min.idle"));
        poolConfig.setMaxIdle(bridgeConfig.getPropertyAsInt("redis.max.idle"));
        poolConfig.setTestOnCreate(true); // test threads when we create them (only)
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);

        // Create pool.
        final String url = getRedisURL();
        final JedisPool jedisPool = constructJedisPool(url, poolConfig);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(jedisPool::destroy));

        return jedisPool;
    }

    /**
     * Try Redis providers to find one that is provisioned. Using this URL in the environment variables
     * is the documented way to interact with these services.
     */
    private String getRedisURL() {
        for (String provider : redisProviders) {
            if (System.getenv(provider) != null) {
                LOG.info("Using Redis Provider: " + provider);
                return System.getenv(provider);
            }
        }
        LOG.info("Using Redis Provider: redis.url");
        return bridgeConfig.getProperty("redis.url");
    }

    private JedisPool constructJedisPool(final String url, final JedisPoolConfig poolConfig)
            throws URISyntaxException {
        // With changes in Redis provisioning, passwords are now parseable by Java's URI class.
        URI redisURI = new URI(url);
        String password = redisURI.getUserInfo().split(":",2)[1];

        if (bridgeConfig.isLocal()) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    bridgeConfig.getPropertyAsInt("redis.timeout"));
        } else {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    bridgeConfig.getPropertyAsInt("redis.timeout"), password);
        }
    }
}
