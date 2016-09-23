package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.redis.JedisOps;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class CacheProviderTest {

    private static final String STRING_KEY = "cache-string-test";
    private static final String STUDY_IDENTIFIER = "cache-study-test";
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String USER_ID = "userId";
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final LinkedHashSet<String> LANGUAGES = TestUtils.newLinkedHashSet("en", "fr");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone MST = DateTimeZone.forOffsetHours(3);
    private static final DateTime ACTIVITIES_REQUESTED_ON = DateUtils.getCurrentDateTime();
    private static final DateTime SIGNED_IN_ON = ACTIVITIES_REQUESTED_ON.minusHours(4);

    @Autowired
    private CacheProvider cacheProvider;
    
    @Autowired
    private BridgeConfig config;
    
    @Autowired
    private JedisOps testJedisOps;
    
    @Resource(name = "redisProviders")
    private List<String> redisProviders;
    
    @After
    public void after() {
        // restore in the in memory redis implementation
        cacheProvider.setJedisOps(testJedisOps);
    }
    
    @Before
    public void before() throws Exception {
        JedisPool jedisPool = constructJedisPool();
        JedisOps jedisOps = new JedisOps(jedisPool);
        cacheProvider.setJedisOps(jedisOps);
        cacheProvider.setSessionExpireInSeconds(4);
    }
    
    // These methods are similar to BridgeProductionSpringConfig. It seems like they could be in bridge-base.
    private JedisPool constructJedisPool() throws URISyntaxException {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        URI redisURI = new URI(getRedisURL());
        String password = redisURI.getUserInfo().split(":",2)[1];

        // similar to production but with a 10 second timeout.
        if (config.isLocal()) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(), 10);
        } else {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(), 10, password);
        }
    }
    
    private String getRedisURL() {
        for (String provider : redisProviders) {
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
        String userId = BridgeUtils.generateGuid();
        String sessionToken = BridgeUtils.generateGuid();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("ABC").withId(userId)
                .build(); 
        UserSession session = new UserSession();
        session.setParticipant(participant);
        session.setSessionToken(sessionToken);
        
        cacheProvider.setUserSession(session);
        
        // get works
        UserSession retrieved = cacheProvider.getUserSession(sessionToken);
        assertNotNull(retrieved);

        // Sleep for a total of 4 seconds, but set/get in the middle of that.
        Thread.sleep(3000);
        cacheProvider.getUserSession(sessionToken);
        cacheProvider.setUserSession(session);
        Thread.sleep(1050);
        
        // still expired after 4 seconds.
        retrieved = cacheProvider.getUserSession(sessionToken);
        assertNull(retrieved);
    }
    
    @Test
    public void canSetAndUpdateRequestInfo() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserId(USER_ID)
                .withUserAgent(USER_AGENT_STRING)
                .withStudyIdentifier(STUDY_ID)
                .withTimeZone(PST)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .build();
        cacheProvider.updateRequestInfo(requestInfo);
        
        // Add different information, rewriting one value
        RequestInfo extraRequestInfo = new RequestInfo.Builder()
                .withUserId(USER_ID)
                .withLanguages(LANGUAGES)
                .withTimeZone(MST)
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withSignedInOn(SIGNED_IN_ON)
                .build();
        cacheProvider.updateRequestInfo(extraRequestInfo);
        
        // Data is combined in cache.
        RequestInfo combinedRequestInfo = cacheProvider.getRequestInfo(USER_ID);
        assertEquals(USER_ID, combinedRequestInfo.getUserId());
        assertEquals(USER_AGENT_STRING, combinedRequestInfo.getUserAgent());
        assertEquals(STUDY_ID, combinedRequestInfo.getStudyIdentifier());
        assertEquals(TestConstants.USER_DATA_GROUPS, combinedRequestInfo.getUserDataGroups());
        assertEquals(LANGUAGES, combinedRequestInfo.getLanguages());
        assertEquals(MST, combinedRequestInfo.getTimeZone());
        assertEquals(ACTIVITIES_REQUESTED_ON.withZone(MST), combinedRequestInfo.getActivitiesAccessedOn());
        assertEquals(SIGNED_IN_ON.withZone(MST), combinedRequestInfo.getSignedInOn());
        
        cacheProvider.removeRequestInfo(USER_ID);
        assertNull(cacheProvider.getRequestInfo(USER_ID));
    }
    
}
