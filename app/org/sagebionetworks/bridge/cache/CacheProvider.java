package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.redis.RedisKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
@Component
public class CacheProvider {
    private ObjectMapper bridgeObjectMapper;
    private JedisOps jedisOps;
    private int sessionExpireInSeconds;

    @Autowired
    final void setBridgeObjectMapper(BridgeObjectMapper bridgeObjectMapper) {
        this.bridgeObjectMapper = bridgeObjectMapper;
    }

    @Resource(name = "jedisOps")
    final void setJedisOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }
    
    @Resource(name = "sessionExpireInSeconds")
    final void setSessionExpireInSeconds(int sessionExpireInSeconds) {
        this.sessionExpireInSeconds = sessionExpireInSeconds;
    }
    
    /**
     * Take existing data in the request info object and augment with any new information 
     * in the request info object passed as a parameter, then persist that. Different calls
     * contribute some different fields to the total RequestInfo object.
     */
    public void updateRequestInfo(RequestInfo requestInfo) {
        checkNotNull(requestInfo, "requestInfo is required");
        checkNotNull(requestInfo.getUserId(), "requestInfo.userId is required");
     
        RequestInfo existingRequestInfo = getRequestInfo(requestInfo.getUserId());
        if (existingRequestInfo != null) {
            RequestInfo.Builder builder = new RequestInfo.Builder();    
            builder.copyOf(existingRequestInfo);
            builder.copyOf(requestInfo);
            setRequestInfo(builder.build());
        } else {
            setRequestInfo(requestInfo);
        }
    }
    
    public void removeRequestInfo(String userId) {
        checkNotNull(userId);
        final String requestInfoKey = RedisKey.REQUEST_INFO.getRedisKey(userId);
        removeObject(requestInfoKey);
    }
    
    private void setRequestInfo(RequestInfo requestInfo) {
        checkNotNull(requestInfo);
        String redisKey = RedisKey.REQUEST_INFO.getRedisKey(requestInfo.getUserId());
        setObject(redisKey, requestInfo);
    }
    
    public RequestInfo getRequestInfo(String userId) {
        checkNotNull(userId);
        String redisKey = RedisKey.REQUEST_INFO.getRedisKey(userId);
        return getObject(redisKey, RequestInfo.class);
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
            
            // If the key exists, get the remaining time to expiration. If it doesn't exist
            // then save with the full expiration period.
            final Long ttl = jedisOps.ttl(userKey);
            final int expiration = (ttl != null && ttl > 0L) ? 
                    ttl.intValue() : sessionExpireInSeconds;
                   
            String ser = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
            
            List<Object> results = transaction
                .setex(userKey, expiration, sessionToken)
                .setex(sessionKey, expiration, ser)
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
            String ser = jedisOps.get(sessionKey);
            if (ser == null) {
                return null;
            }
            return bridgeObjectMapper.readValue(ser, UserSession.class);
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public UserSession getUserSessionByUserId(final String userId) {
        checkNotNull(userId);
        
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            // This key isn't stored as a JSON string, retrieve it directly
            String sessionToken = jedisOps.get(userKey);
            return (sessionToken == null) ? null : getUserSession(sessionToken);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
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
        checkNotNull(userId);
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            String sessionToken = jedisOps.get(userKey);
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
        checkNotNull(study);
        String redisKey = RedisKey.STUDY.getRedisKey(study.getIdentifier());
        setObject(redisKey, study, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public Study getStudy(String identifier) {
        checkNotNull(identifier);
        String redisKey = RedisKey.STUDY.getRedisKey(identifier);
        return getObject(redisKey, Study.class, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public void removeStudy(String identifier) {
        checkNotNull(identifier);
        String redisKey = RedisKey.STUDY.getRedisKey(identifier);
        removeObject(redisKey);
    }

    public <T> T getObject(String cacheKey, Class<T> clazz) {
        checkNotNull(cacheKey);
        checkNotNull(clazz);
        try {
            String ser = jedisOps.get(cacheKey);
            if (ser != null) {
                return bridgeObjectMapper.readValue(ser, clazz);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }
    
    public <T> T getObject(String cacheKey, TypeReference<T> typeRef) {
        checkNotNull(cacheKey);
        checkNotNull(typeRef);
        try {
            String ser = jedisOps.get(cacheKey);
            if (ser != null) {
                return bridgeObjectMapper.readValue(ser, typeRef);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }
    
    /**
     * Get the object, resetting its expiration period.
     */
    public <T> T getObject(String cacheKey, Class<T> clazz, int expireInSeconds) {
        checkNotNull(cacheKey);
        checkNotNull(clazz);
        try {
            String ser = jedisOps.get(cacheKey);
            if (ser != null) {
                jedisOps.expire(cacheKey, expireInSeconds);
                return bridgeObjectMapper.readValue(ser, clazz);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }
    
    /**
     * Set an object in the cache with no expiration.
     */
    public void setObject(String cacheKey, Object object) {
        checkNotNull(cacheKey);
        checkNotNull(object);
        try {
            String ser = bridgeObjectMapper.writeValueAsString(object);
            String result = jedisOps.set(cacheKey, ser);
            if (!"OK".equals(result)) {
                throw new BridgeServiceException(object.getClass().getSimpleName() + " storage error");
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    /**
     * Set an object in the cache with an expiration in seconds
     */
    public void setObject(String cacheKey, Object object, int expireInSeconds) {
        checkNotNull(cacheKey);
        checkNotNull(object);
        try {
            String ser = bridgeObjectMapper.writeValueAsString(object);
            String result = jedisOps.setex(cacheKey, expireInSeconds, ser);
            if (!"OK".equals(result)) {
                throw new BridgeServiceException(object.getClass().getSimpleName() + " storage error");
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    /**
     * Remove object from cache, if it exists.
     */
    public void removeObject(String cacheKey) {
        checkNotNull(cacheKey);
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
