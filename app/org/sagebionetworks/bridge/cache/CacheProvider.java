package org.sagebionetworks.bridge.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
@Component
public class CacheProvider {

    private JedisOps jedisOps;

    @Autowired
    public void setJedisOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }

    public void setUserSession(final UserSession session) {
        checkNotNull(session);
        List<Object> results = null;
        try {
            final String userId = session.getUser().getId();
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            final String sessionToken = session.getSessionToken();
            final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
            final String ser = BridgeObjectMapper.get().writeValueAsString(session);
            results = jedisOps.getTransaction()
                    .setex(userKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, sessionToken)
                    .setex(sessionKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser)
                    .exec();
        } catch (JsonProcessingException e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
        if (results == null) {
            throw new BridgeServiceException("Session storage error.");
        }
        for (Object result : results) {
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("Session storage error.");
            }
        }
    }

    public UserSession getUserSession(final String sessionToken) {
        try {
            final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
            final String ser = jedisOps.get(sessionKey);
            if (ser != null) {
                final UserSession session = BridgeObjectMapper.get().readValue(ser, UserSession.class);
                final String userKey = RedisKey.USER_SESSION.getRedisKey(session.getUser().getId());
                jedisOps.getTransaction(sessionToken)
                        .expire(userKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS)
                        .expire(sessionKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS)
                        .exec();
                return session;
            }
            return null;
        } catch (Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void removeSession(final UserSession session) {
        try {
            final String sessionKey = RedisKey.SESSION.getRedisKey(session.getSessionToken());
            final String userKey = RedisKey.USER_SESSION.getRedisKey(session.getUser().getId());
            jedisOps.getTransaction().del(sessionKey).del(userKey).exec();
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void removeSessionByUserId(final String userId) {
        try {
            final String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
            final String sessionToken = jedisOps.get(userKey);
            final String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
            jedisOps.getTransaction().del(sessionKey).del(userKey).exec();
        } catch(Throwable e) {
            promptToStartRedisIfLocal(e);
            throw new BridgeServiceException(e);
        }
    }

    public void setStudy(Study study) {
        try {
            String ser = BridgeObjectMapper.get().writeValueAsString(study);
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
                return DynamoStudy.fromCacheJson(BridgeObjectMapper.get().readTree(ser));
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

    public void setString(String cacheKey, String value) {
        try {
            String result = jedisOps.setex(cacheKey, BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS, value);
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
        if (e.getClass() == JedisException.class && BridgeConfigFactory.getConfig().isLocal()) {
            throw new BridgeServiceException(
                    "Cannot find cache service, have you started a Redis server? (original message: "
                    + e.getMessage() + ")");
        }
    }
}
