package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.redis.RedisKey;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
@Component
public class CacheProvider {

    private static final TypeReference<Map<SubpopulationGuid, ConsentStatus>> CONSENT_MAP_REFERENCE = new TypeReference<Map<SubpopulationGuid, ConsentStatus>>() {};
    
    private ObjectMapper bridgeObjectMapper;
    private JedisOps jedisOps;

    @Autowired
    public void setBridgeObjectMapper(BridgeObjectMapper bridgeObjectMapper) {
        this.bridgeObjectMapper = bridgeObjectMapper;
    }

    @Autowired
    public void setJedisOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }

    public void setUserSession(final UserSession session) {
        checkNotNull(session);
        checkNotNull(session.getParticipant(), "Missing participant in session.");
        checkNotNull(session.getHealthCode(), "Missing healthCode in session.");
        checkNotNull(session.getId(), "Missing user ID in session.");
        checkNotNull(session.getSessionToken(), "Missing session token for session.");

        final String userId = session.getId();
        final String sessionToken = session.getSessionToken();
        
        final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        try (JedisTransaction transaction = jedisOps.getTransaction()) {
            final String ser = bridgeObjectMapper.writeValueAsString(session);
            final List<Object> results = transaction
                    .setex(userKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, sessionToken)
                    .setex(sessionKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser)
                    .exec();
            if (results == null) {
                throw new BridgeServiceException("Session storage error.");
            }
            for (Object result : results) {
                if (!"OK".equals(result)) {
                    throw new BridgeServiceException("Session storage error.");
                }
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public UserSession getUserSession(final String sessionToken) {
        checkNotNull(sessionToken);
        try {
            final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
            final String ser = jedisOps.get(sessionKey);
            if (ser == null) {
                return null;
            }
            JsonNode node = bridgeObjectMapper.readTree(ser);
            UserSession session = bridgeObjectMapper.treeToValue(node, UserSession.class);
            // This is special processing to migrate old versions of the session (that have a user object)
            // to the newer session structure. Once we're sure we're rotating sessions, and this no longer
            // exists in any session, it can be removed.
            if (node.has("user")) {
                JsonNode userNode = node.get("user");
                JsonNode consentNode = node.get("user").get("consentStatuses");
                
                StudyParticipant participant = bridgeObjectMapper.treeToValue(userNode, StudyParticipant.class);
                if (userNode.has("accountCreatedOn")) {
                    DateTime createdOn = DateTime.parse(userNode.get("accountCreatedOn").asText());
                    participant = new StudyParticipant.Builder().copyOf(participant)
                            .withCreatedOn(createdOn).build();
                }
                session.setParticipant(participant);
                
                Map<SubpopulationGuid,ConsentStatus> statuses = bridgeObjectMapper.convertValue(consentNode, CONSENT_MAP_REFERENCE);
                session.setConsentStatuses(statuses);
            }
            final String userKey = RedisKey.USER_SESSION.getRedisKey(session.getId());
            try (JedisTransaction transaction = jedisOps.getTransaction(sessionKey)) {
                transaction
                        .expire(userKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS)
                        .expire(sessionKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS)
                        .exec();
            }
            return session;
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public UserSession getUserSessionByUserId(final String userId) {
        checkNotNull(userId);
        String sessionToken = null;
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            sessionToken = jedisOps.get(userKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        if (sessionToken == null) {
            return null;
        }
        return getUserSession(sessionToken);
    }

    public void removeSession(final UserSession session) {
        checkNotNull(session.getSessionToken());
        checkNotNull(session.getId());
        try {
            final String sessionKey = RedisKey.SESSION.getRedisKey(session.getSessionToken());
            final String userKey = RedisKey.USER_SESSION.getRedisKey(session.getId());
            try (JedisTransaction transaction = jedisOps.getTransaction()) {
                transaction.del(sessionKey).del(userKey).exec();
            }
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void removeSessionByUserId(final String userId) {
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            final String sessionToken = jedisOps.get(userKey);
            if (sessionToken != null) {
                final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
                try (JedisTransaction transaction = jedisOps.getTransaction()) {
                    transaction.del(sessionKey).del(userKey).exec();
                }
            }
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void setStudy(Study study) {
        try {
            String ser = bridgeObjectMapper.writeValueAsString(study);
            String redisKey = RedisKey.STUDY.getRedisKey(study.getIdentifier());
            String result = jedisOps.setex(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("Study storage error");
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public Study getStudy(String identifier) {
        try {
            String redisKey = RedisKey.STUDY.getRedisKey(identifier);
            String ser = jedisOps.get(redisKey);
            if (ser != null) {
                jedisOps.expire(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
                return bridgeObjectMapper.readValue(ser, Study.class);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }

    public void removeStudy(String identifier) {
        try {
            String redisKey = RedisKey.STUDY.getRedisKey(identifier);
            jedisOps.del(redisKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public String getString(String cacheKey) {
        try {
            return jedisOps.get(cacheKey);
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void setString(String cacheKey, String value, int expireInSeconds) {
        try {
            String result = jedisOps.setex(cacheKey, expireInSeconds, value);
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("View storage error");
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void removeString(String cacheKey) {
        try {
            jedisOps.del(cacheKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    private void promptToStartRedisIfLocal(Throwable e) {
        if (BridgeConfigFactory.getConfig().isLocal()) {
            throw new BridgeServiceException(
                    "Cannot find cache service, have you started a Redis server? (original message: "
                    + e.getMessage() + ")");
        }
    }
}
