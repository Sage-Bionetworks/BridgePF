package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.redis.RedisKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
@Component
public class CacheProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CacheProvider.class);
    
    private ObjectMapper bridgeObjectMapper;
    private JedisOps oldJedisOps;
    private JedisOps newJedisOps;
    private int sessionExpireInSeconds;

    @Autowired
    final void setBridgeObjectMapper(BridgeObjectMapper bridgeObjectMapper) {
        this.bridgeObjectMapper = bridgeObjectMapper;
    }

    @Resource(name = "oldJedisOps")
    final void setOldJedisOps(JedisOps oldJedisOps) {
        this.oldJedisOps = oldJedisOps;
    }
    
    @Resource(name = "newJedisOps")
    final void setNewJedisOps(JedisOps newJedisOps) {
        this.newJedisOps = newJedisOps;
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
        try {
            final String requestInfoKey = RedisKey.REQUEST_INFO.getRedisKey(userId);
            oldJedisOps.del(requestInfoKey);
            newJedisOps.del(requestInfoKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    private void setRequestInfo(RequestInfo requestInfo) {
        try {
            String ser = bridgeObjectMapper.writeValueAsString(requestInfo);
            String redisKey = RedisKey.REQUEST_INFO.getRedisKey(requestInfo.getUserId());
            newJedisOps.set(redisKey, ser);
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        
    }
    
    public RequestInfo getRequestInfo(String userId) {
        try {
            String redisKey = RedisKey.REQUEST_INFO.getRedisKey(userId);
            String ser = getWithFallback(redisKey, false);
            if (ser != null) {
                return bridgeObjectMapper.readValue(ser, RequestInfo.class);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        return null;
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
        try (JedisTransaction transaction = newJedisOps.getTransaction()) {
            
            // If the key exists, get the remaining time to expiration. If it doesn't exist
            // then save with the full expiration period.
            final Long ttl = newJedisOps.ttl(userKey);
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
            String ser = getWithFallback(sessionKey, false);
            if (ser == null) {
                return null;
            }
            try {
                return bridgeObjectMapper.readValue(ser, UserSession.class);
            } catch (IOException ex) {
                // Because StudyParticipant.Builder.withEncryptedHealthCode() can throw, and because of the way
                // Jackson's BuilderBasedDeserializer, the error message actually contains the JSON. To prevent leaking
                // personal identifying info, we should squelch the error message and replace it with our own.
                throw new BridgeServiceException("Error parsing JSON for session " + sessionToken);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public UserSession getUserSessionByUserId(final String userId) {
        checkNotNull(userId);
        String sessionToken;
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            sessionToken = getWithFallback(userKey, false);
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
            try (JedisTransaction transaction = oldJedisOps.getTransaction()) {
                transaction.del(sessionKey).del(userKey).exec();
            }
            try (JedisTransaction transaction = newJedisOps.getTransaction()) {
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
            String sessionToken = oldJedisOps.get(userKey);
            if (sessionToken != null) {
                final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
                try (JedisTransaction transaction = oldJedisOps.getTransaction()) {
                    transaction.del(sessionKey).del(userKey).exec();
                }
            }
            sessionToken = newJedisOps.get(userKey);
            if (sessionToken != null) {
                final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
                try (JedisTransaction transaction = newJedisOps.getTransaction()) {
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
            String result = newJedisOps.setex(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
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
            String ser = getWithFallback(redisKey, true);
            if (ser != null) {
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
            oldJedisOps.del(redisKey);
            newJedisOps.del(redisKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public String getString(String cacheKey) {
        try {
            return getWithFallback(cacheKey, false);
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void setString(String cacheKey, String value, int expireInSeconds) {
        try {
            String result = newJedisOps.setex(cacheKey, expireInSeconds, value);
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
            oldJedisOps.del(cacheKey);
            newJedisOps.del(cacheKey);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void setSignIn(String token, String reverseLookupKey, SignIn signIn, int expireInSeconds) {
        try (JedisTransaction transaction = newJedisOps.getTransaction()) {
            String ser = BridgeObjectMapper.get().writeValueAsString(signIn);
            List<Object> results = transaction
                .setex(token, expireInSeconds, ser)
                .setex(reverseLookupKey, expireInSeconds, token)
                .exec();
            if (results == null) {
                throw new BridgeServiceException("Storage error.");
            }
            for (Object result : results) {
                if (!"OK".equals(result)) {
                    throw new BridgeServiceException("Storage error.");
                }
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public String hasSignInToken(String reverseLookupKey) {
        try {
            return newJedisOps.get(reverseLookupKey);
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public SignIn getSignIn(String token) {
        try {
            String ser = newJedisOps.get(token);
            if (ser != null) {
                return BridgeObjectMapper.get().readValue(ser, SignIn.class);
            }
            return null;
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public void removeSignIn(String token, String reverseLookupKey) {
        try {
            newJedisOps.del(reverseLookupKey);
            newJedisOps.del(token);
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }
    
    private String getWithFallback(String key, boolean expireIfFound) {
        String ser = newJedisOps.get(key);
        if (ser != null) {
            if (expireIfFound) {
                newJedisOps.expire(key, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
            }
            return ser;
        }
        ser = oldJedisOps.get(key);
        if (ser != null) {
            LOG.info("Retrieving data from old Redis instance: " + key);
            if (expireIfFound) {
                oldJedisOps.expire(key, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
            }
            return ser;
        }
        return null;
    }
    
    private void promptToStartRedisIfLocal(Throwable e) {
        if (BridgeConfigFactory.getConfig().isLocal()) {
            throw new BridgeServiceException(
                    "Cannot find cache service, have you started a Redis server? (original message: "
                    + e.getMessage() + ")");
        }
    }
}
