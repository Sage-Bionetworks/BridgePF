package org.sagebionetworks.bridge.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.annotation.Resource;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final List<String> REDIS_PROVIDERS = Lists.newArrayList("REDISCLOUD_URL", "REDISTOGO_URL");

    @Bean(name = "jedisOps")
    @Resource(name = "jedisPool")
    public JedisOps jedisOps(final JedisPool jedisPool) {
        return new JedisOps(jedisPool);
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool(BridgeConfig config) throws Exception {
        // Configure pool
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getPropertyAsInt("redis.max.total"));
        poolConfig.setMinIdle(config.getPropertyAsInt("redis.min.idle"));
        poolConfig.setMaxIdle(config.getPropertyAsInt("redis.max.idle"));
        poolConfig.setTestOnCreate(true); // test threads when we create them (only)
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);

        // Create pool.
        final String url = getRedisURL(config);
        final JedisPool jedisPool = constructJedisPool(url, poolConfig, config);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(jedisPool::destroy));

        return jedisPool;
    }

    /**
     * Try Redis providers to find one that is provisioned. Using this URL in the environment variables
     * is the documented way to interact with these services.
     */
    private String getRedisURL(final BridgeConfig config) {
        for (String provider : REDIS_PROVIDERS) {
            if (System.getenv(provider) != null) {
                LOG.info("Using Redis Provider: " + provider);
                return System.getenv(provider);
            }
        }
        LOG.info("Using Redis Provider: redis.url");
        return config.getProperty("redis.url");
    }

    private JedisPool constructJedisPool(final String url, final JedisPoolConfig poolConfig, final BridgeConfig config)
            throws URISyntaxException {
        // With changes in Redis provisioning, passwords are now parseable by Java's URI class.
        URI redisURI = new URI(url);
        String password = redisURI.getUserInfo().split(":",2)[1];

        if (config.isLocal()) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    config.getPropertyAsInt("redis.timeout"));
        } else {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    config.getPropertyAsInt("redis.timeout"), password);
        }
    }
}
