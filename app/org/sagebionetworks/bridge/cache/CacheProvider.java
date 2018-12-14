package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * A wrapper around our use of Redis.
 */
@Component
public class CacheProvider {
    private static final String LOCAL_SERVICE_ERROR = "Cannot find cache service, have you started Redis? (original message: %s)";
    private JedisOps jedisOps;
    private int sessionExpireInSeconds;

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
        CacheKey redisKey = CacheKey.requestInfo(userId);
        removeObject(redisKey);
    }
    
    private void setRequestInfo(RequestInfo requestInfo) {
        checkNotNull(requestInfo);
        CacheKey redisKey = CacheKey.requestInfo(requestInfo.getUserId());
        setObject(redisKey, requestInfo);
    }
    
    public RequestInfo getRequestInfo(String userId) {
        checkNotNull(userId);
        CacheKey redisKey = CacheKey.requestInfo(userId);
        return getObject(redisKey, RequestInfo.class);
    }

    public void setUserSession(UserSession session) {
        checkNotNull(session);
        checkNotNull(session.getSessionToken());
        checkNotNull(session.getId());
        
        CacheKey tokenToUserIdKey = CacheKey.tokenToUserId(session.getSessionToken());
        CacheKey userIdToSessionKey = CacheKey.userIdToSession(session.getId());
        
        try (JedisTransaction transaction = jedisOps.getTransaction()) {
            // If the key exists, get the remaining time to expiration. If it doesn't exist
            // then save with the full expiration period.
            Long ttl = jedisOps.ttl(userIdToSessionKey.toString());
            int expiration = (ttl != null && ttl > 0L) ? ttl.intValue() : sessionExpireInSeconds;
                   
            String ser = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
            
            List<Object> results = transaction
                .setex(tokenToUserIdKey.toString(), expiration, session.getId())
                .setex(userIdToSessionKey.toString(), expiration, ser)
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
    
    public UserSession getUserSession(String sessionToken) {
        checkNotNull(sessionToken);
        try {
            CacheKey tokenToUserIdKey = CacheKey.tokenToUserId(sessionToken);
            String userId = jedisOps.get(tokenToUserIdKey.toString());
            if (userId != null) {
                CacheKey userIdToSessionKey = CacheKey.userIdToSession(userId);
                String ser = jedisOps.get(userIdToSessionKey.toString());
                if (ser != null) {
                    UserSession session = BridgeObjectMapper.get().readValue(ser, UserSession.class);
                    // The token --> userId look up is not replaced on session invalidation. 
                    // Check here and only return if the sessionToken is valid. It is possible 
                    // to successfully sign in and then have this fail due to concurrent requests.
                    // The client needs to manage concurrent requests if it doesn't want to 
                    // invalidate its own session.
                    if (session.getSessionToken().equals(sessionToken)) {
                        return session;
                    }
                    // Otherwise, delete the key sessionToken key (it's known to be invalid)
                    jedisOps.del(tokenToUserIdKey.toString());
                }
            }
            return null;
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public UserSession getUserSessionByUserId(String userId) {
        checkNotNull(userId);
        
        try {
            CacheKey userIdToSessionKey = CacheKey.userIdToSession(userId);
            String ser = jedisOps.get(userIdToSessionKey.toString());
            if (ser == null) {
                return null;
            }
            return BridgeObjectMapper.get().readValue(ser, UserSession.class);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void removeSession(UserSession session) {
        checkNotNull(session);
        checkNotNull(session.getSessionToken());
        checkNotNull(session.getId());
        
        CacheKey tokenToUserIdKey = CacheKey.tokenToUserId(session.getSessionToken());
        CacheKey userIdToSessionKey = CacheKey.userIdToSession(session.getId());
        
        try (JedisTransaction transaction = jedisOps.getTransaction()) {
            transaction
                .del(tokenToUserIdKey.toString())
                .del(userIdToSessionKey.toString())
                .exec();
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void removeSessionByUserId(final String userId) {
        checkNotNull(userId);

        UserSession session = getUserSessionByUserId(userId);
        if (session != null) {
            removeSession(session);
        }
    }

    public void setStudy(Study study) {
        checkNotNull(study);
        CacheKey redisKey = CacheKey.study(study.getIdentifier());
        setObject(redisKey, study, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public Study getStudy(String identifier) {
        checkNotNull(identifier);
        CacheKey redisKey = CacheKey.study(identifier);
        return getObject(redisKey, Study.class, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    public void removeStudy(String identifier) {
        checkNotNull(identifier);
        CacheKey redisKey = CacheKey.study(identifier);
        removeObject(redisKey);
    }

    public <T> T getObject(CacheKey cacheKey, Class<T> clazz) {
        checkNotNull(cacheKey);
        checkNotNull(clazz);
        try {
            String ser = jedisOps.get(cacheKey.toString());
            if (ser != null) {
                return BridgeObjectMapper.get().readValue(ser, clazz);
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
                return BridgeObjectMapper.get().readValue(ser, typeRef);
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
                return BridgeObjectMapper.get().readValue(ser, clazz);
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
            String ser = BridgeObjectMapper.get().writeValueAsString(object);
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
            String ser = BridgeObjectMapper.get().writeValueAsString(object);
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
            throw new BridgeServiceException(String.format(LOCAL_SERVICE_ERROR, e.getMessage()));
        }
    }
}
