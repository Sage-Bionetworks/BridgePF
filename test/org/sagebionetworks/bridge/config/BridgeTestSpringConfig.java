package org.sagebionetworks.bridge.config;

import static org.mockito.Mockito.mock;

import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.redis.InMemoryJedisOps;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import redis.clients.jedis.JedisPool;

/**
 * Test-only Spring config that mocks out things we don't want in our test configs for reliability/repeatability
 * concerns, most notably Redis.
 */
@Configuration
@ImportResource("classpath:shared-config.xml")
public class BridgeTestSpringConfig {
    @Bean(name = "jedisOps")
    public JedisOps jedisOps() {
        return new InMemoryJedisOps();
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool() {
        // Since we don't connect to a real host, pass in "localhost" as a dummy hostname
        // This is only used by CacheAdminService, the test of which is fully mocked. Just use a Mockito mock.
        return mock(JedisPool.class);
    }

    @Bean(name = "testUserAdminHelper")
    public TestUserAdminHelper getTestUserAdminHelper() {
        return new TestUserAdminHelper();
    }
}
