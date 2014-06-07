package org.sagebionetworks.bridge.cache;

import java.io.IOException;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.redis.JedisStringOps;

import com.fasterxml.jackson.core.JsonProcessingException;
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
            String result = stringOps.setex(key, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser).execute();
            if (!"OK".equals(result)) {
                throw new BridgeServiceException("Session storage error", 500);
            }
        } catch (JsonProcessingException e) {
            throw new BridgeServiceException(e, 500);
        }
    }
    
    public UserSession getUserSession(String key) {
        try {
            String ser = stringOps.get(key).execute();
            if (ser != null) {
                return mapper.readValue(ser, UserSession.class);  
            }
        } catch (IOException e) {
            throw new BridgeServiceException(e, 500);
        }
        return null;
    }
    
    public void remove(String key) {
        stringOps.delete(key).execute();
    }

}
