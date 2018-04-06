package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheKeys.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;

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
        CacheKey redisKey = CacheKeys.requestInfo(userId);
        removeObject(redisKey);
    }
    
    private void setRequestInfo(RequestInfo requestInfo) {
        checkNotNull(requestInfo);
        CacheKey redisKey = CacheKeys.requestInfo(requestInfo.getUserId());
        setObject(redisKey, requestInfo);
    }
    
    public RequestInfo getRequestInfo(String userId) {
        checkNotNull(userId);
        CacheKey redisKey = CacheKeys.requestInfo(userId);
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
        
        final CacheKey userKey = CacheKeys.sessionByUserId(userId);
        final CacheKey sessionKey = CacheKeys.session(sessionToken);
        
        try (JedisTransaction transaction = jedisOps.getTransaction()) {
            
            // If the key exists, get the remaining time to expiration. If it doesn't exist
            // then save with the full expiration period.
            final Long ttl = jedisOps.ttl(userKey.toString());
            final int expiration = (ttl != null && ttl > 0L) ? 
                    ttl.intValue() : sessionExpireInSeconds;
                   
            String ser = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
            
            List<Object> results = transaction
                .setex(userKey.toString(), expiration, sessionToken)
                .setex(sessionKey.toString(), expiration, ser)
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
            final CacheKey sessionKey = CacheKeys.session(sessionToken);
            String ser = jedisOps.get(sessionKey.toString());
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
            final CacheKey userKey = CacheKeys.sessionByUserId(userId);
            // This key isn't stored as a JSON string, retrieve it directly
            String sessionToken = jedisOps.get(userKey.toString());
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
            final CacheKey sessionKey = CacheKeys.session(session.getSessionToken());
            final CacheKey userKey = CacheKeys.sessionByUserId(session.getId());
            try (JedisTransaction transaction = jedisOps.getTransaction()) {
                transaction.del(sessionKey.toString())
                    .del(userKey.toString()).exec();
            }
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void removeSessionByUserId(final String userId) {
        checkNotNull(userId);
        try {
            final CacheKey userKey = CacheKeys.sessionByUserId(userId);
            String sessionToken = jedisOps.get(userKey.toString());
            if (sessionToken != null) {
                final CacheKey sessionKey = CacheKeys.session(sessionToken);
                try (JedisTransaction transaction = jedisOps.getTransaction()) {
                    transaction.del(sessionKey.toString())
                        .del(userKey.toString()).exec();
                }
            }
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void setStudy(Study study) {
        checkNotNull(study);
        CacheKey redisKey = CacheKeys.study(study.getIdentifier());
        setObject(redisKey, study, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public Study getStudy(String identifier) {
        checkNotNull(identifier);
        CacheKey redisKey = CacheKeys.study(identifier);
        return getObject(redisKey, Study.class, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public void removeStudy(String identifier) {
        checkNotNull(identifier);
        CacheKey redisKey = CacheKeys.study(identifier);
        removeObject(redisKey);
    }

    public <T> T getObject(CacheKey cacheKey, Class<T> clazz) {
        checkNotNull(cacheKey);
        checkNotNull(clazz);
        try {
            String ser = jedisOps.get(cacheKey.toString());
            if (ser != null) {
                return bridgeObjectMapper.readValue(ser, clazz);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }
    
    public <T> T getObject(CacheKey cacheKey, TypeReference<T> typeRef) {
        checkNotNull(cacheKey);
        checkNotNull(typeRef);
        try {
            String ser = jedisOps.get(cacheKey.toString());
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
    public <T> T getObject(CacheKey cacheKey, Class<T> clazz, int expireInSeconds) {
        checkNotNull(cacheKey);
        checkNotNull(clazz);
        try {
            String ser = jedisOps.get(cacheKey.toString());
            if (ser != null) {
                jedisOps.expire(cacheKey.toString(), expireInSeconds);
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
    public void setObject(CacheKey cacheKey, Object object) {
        checkNotNull(cacheKey);
        checkNotNull(object);
        try {
            String ser = bridgeObjectMapper.writeValueAsString(object);
            String result = jedisOps.set(cacheKey.toString(), ser);
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
    public void setObject(CacheKey cacheKey, Object object, int expireInSeconds) {
        checkNotNull(cacheKey);
        checkNotNull(object);
        try {
            String ser = bridgeObjectMapper.writeValueAsString(object);
            String result = jedisOps.setex(cacheKey.toString(), expireInSeconds, ser);
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
    public void removeObject(CacheKey cacheKey) {
        checkNotNull(cacheKey);
        try {
            jedisOps.del(cacheKey.toString());
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }        
    }
    
    public void addCacheKeyToSet(CacheKey cacheKeyOfSet, String cacheKeyInSet) {
        checkNotNull(cacheKeyOfSet);
        checkNotNull(cacheKeyInSet);
        try {
            jedisOps.sadd(cacheKeyOfSet.toString(), cacheKeyInSet);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void removeSetOfCacheKeys(CacheKey cacheKeyOfSet) {
        checkNotNull(cacheKeyOfSet);
        
        try {
            Set<String> members = jedisOps.smembers(cacheKeyOfSet.toString());
            if (members != null && !members.isEmpty()) {
                try (JedisTransaction transaction = jedisOps.getTransaction()) {
                    for (String oneMember : members) {
                        transaction.del(oneMember);
                    }
                    transaction.del(cacheKeyOfSet.toString());
                    transaction.exec();
                }
            }
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
