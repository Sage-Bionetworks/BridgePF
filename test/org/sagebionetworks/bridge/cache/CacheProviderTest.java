package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeProductionSpringConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class CacheProviderTest {

    private static final String STRING_KEY = "cache-string-test";

    private static final String STUDY_IDENTIFIER = "cache-study-test";

    @Autowired
    private CacheProvider cacheProvider;
    
    @Autowired
    private BridgeConfig config;
    
    @Autowired
    private JedisOps testJedisOps;
    
    @After
    public void after() {
        cacheProvider.setJedisOps(testJedisOps);
    }
    
    @Before
    public void before() throws Exception {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();

        URI redisURI = new URI(getRedisURL());
        JedisPool jedisPool = new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(), 10); //10 second timeout
        JedisOps jedisOps = new JedisOps(jedisPool);
        cacheProvider.setJedisOps(jedisOps);
        cacheProvider.setSessionExpireInSeconds(4);
    }
    
    // It is necessary to use the redis server in the environment where tests are running, just as in the 
    // BridgeProductionSpringConfig configuration of the jedisOps class.
    private String getRedisURL() {
        for (String provider : BridgeProductionSpringConfig.REDIS_PROVIDERS) {
            if (System.getenv(provider) != null) {
                return System.getenv(provider);
            }
        }
        return config.getProperty("redis.url");
    }

    
    @Test
    public void stringCachingWorks() {
        cacheProvider.setString(STRING_KEY, "cache-test-value", BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        String retrieved = cacheProvider.getString(STRING_KEY);
        assertEquals("cache-test-value", retrieved);
    }
    
    @Test
    public void studyCachingWorks() {
        Study study = new DynamoStudy();
        study.setIdentifier(STUDY_IDENTIFIER);
        
        cacheProvider.setStudy(study);
        Study retrieved = cacheProvider.getStudy(STUDY_IDENTIFIER);
        assertEquals(STUDY_IDENTIFIER, retrieved.getIdentifier());
    }
    
    @Test
    public void canSetAndResetSessionWithoutResettingExpiration() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("ABC").withId("id")
                .build(); 
        UserSession session = new UserSession();
        session.setParticipant(participant);
        session.setSessionToken("cache-test-session-token");
        
        cacheProvider.setUserSession(session);
        
        // get works
        UserSession retrieved = cacheProvider.getUserSession("cache-test-session-token");
        assertNotNull(retrieved);

        // Sleep for a total of 4 seconds, but set/get in the middle of that.
        Thread.sleep(3000);
        cacheProvider.getUserSession("cache-test-session-token");
        cacheProvider.setUserSession(session);
        Thread.sleep(1000);
        
        // still expired after 4 seconds.
        retrieved = cacheProvider.getUserSession("cache-test-session-token");
        assertNull(retrieved);
    }
    
}
