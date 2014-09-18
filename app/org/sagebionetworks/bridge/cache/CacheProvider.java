package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
public class CacheProvider {
    
    private JedisStringOps stringOps = new JedisStringOps();
    
    private ObjectMapper mapper = new ObjectMapper();
            
    public void setUserSession(String key, UserSession session) {
        try {
            String ser = mapper.writeValueAsString(session);
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            String result = stringOps.set(redisKey, ser).execute();
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("Session storage error", 500);
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e, 500);
        }
    }
    
    public UserSession getUserSession(String key) {
        try {
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            String ser = stringOps.get(redisKey).execute();
            if (ser != null) {
                return mapper.readValue(ser, UserSession.class);  
            }
        } catch (Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e, 500);
        }
        return null;
    }
    
    public void remove(String key) {
        try {
            String redisKey = RedisKey.SESSION.getRedisKey(key);
            stringOps.delete(redisKey).execute();
        } catch(Throwable e) {
            promptToStartRedisIfLocalEnv(e);
            throw new BridgeServiceException(e, 500);
        }
    }
    
    private void promptToStartRedisIfLocalEnv(Throwable e) {
        if (BridgeConfigFactory.getConfig().isLocal()) {
            throw new BridgeServiceException("Cannot find cache service, have you started a Redis server? (original message: "+e.getMessage()+")", 500);
        }
    }

}
