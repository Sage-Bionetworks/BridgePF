package org.sagebionetworks.bridge.config;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.redis.JedisOps;

/**
 * Production-only Spring config. This includes things that we don't want in our unit tests for
 * reliability/repeatability concerns, most notably Redis
 */
@Configuration
public class BridgeProductionSpringConfig {

    @Autowired
    BridgeConfig bridgeConfig;
    
    @Bean(name = "jedisOps")
    @Resource(name = "jedisPool")
    public JedisOps jedisOps(final JedisPool jedisPool) {
        return new JedisOps(jedisPool);
    }

    @Bean(name = "newJedisOps")
    @Resource(name = "newJedisPool")
    public JedisOps newJedisOps(final JedisPool jedisPool) {
        return new JedisOps(jedisPool);
    }
    
    @Bean(name = "jedisPool")
    public JedisPool jedisPool() throws Exception {
        return createJedisPool("rediscloud.url");
    }

    @Bean(name = "newJedisPool")
    public JedisPool newJedisPool() throws Exception {
        return createJedisPool("elasticache.url");
    }
    
    private JedisPool createJedisPool(String redisServerProperty) throws Exception {
        
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(bridgeConfig.getPropertyAsInt("redis.max.total"));
        poolConfig.setMinIdle(bridgeConfig.getPropertyAsInt("redis.min.idle"));
        poolConfig.setMaxIdle(bridgeConfig.getPropertyAsInt("redis.max.idle"));
        poolConfig.setTestOnCreate(true); // test threads when we create them (only)
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);
        
        final String url = bridgeConfig.get(redisServerProperty);
        final JedisPool jedisPool = constructJedisPool(url, poolConfig);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(jedisPool::destroy));

        return jedisPool;
    }
    
    private JedisPool constructJedisPool(final String url, final JedisPoolConfig poolConfig)
            throws URISyntaxException {

        URI redisURI = new URI(url);
        String password = BridgeUtils.extractPasswordFromURI(redisURI);
        
        if (password != null) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    bridgeConfig.getPropertyAsInt("redis.timeout"), password);
        }
        return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                bridgeConfig.getPropertyAsInt("redis.timeout"));
    }
}
