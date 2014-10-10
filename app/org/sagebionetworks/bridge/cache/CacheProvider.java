package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
public class CacheProvider {
    
    private JedisStringOps stringOps = new JedisStringOps();
    
    public void setUserSession(String key, UserSession session) {
        try {
            String ser = BridgeObjectMapper.get().writeValueAsString(session);
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            String result = stringOps.setex(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser).execute();
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("Session storage error");
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e);
        }
    }
    
    public UserSession getUserSession(String key) {
        try {
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            String ser = stringOps.get(redisKey).execute();
            if (ser != null) {
                stringOps.expire(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
                return BridgeObjectMapper.get().readValue(ser, UserSession.class);  
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e);
        }
        return null;
    }
    
    public void remove(String key) {
        try {
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            stringOps.delete(redisKey).execute();
        } catch(Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e);
        }
    }
    
    private void promptToStartRedisIfLocalEnv(Throwable e) {
        if (BridgeConfigFactory.getConfig().isLocal()) {
            throw new BridgeServiceException("Cannot find cache service, have you started a Redis server? (original message: "+e.getMessage()+")");
        }
    }

}
