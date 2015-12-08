package org.sagebionetworks.bridge.redis;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.EnumSet;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

abstract class AbstractRedisKey implements RedisKey {

    private final BridgeConfig config =  BridgeConfigFactory.getConfig();
    private final EnumSet<Environment> SHARED_ENV = EnumSet.of(Environment.DEV, Environment.LOCAL);
    
    @Override
    public String getRedisKey(String key) {
        checkNotNull(key);
        return keyForSharedRedis(key + RedisKey.SEPARATOR + getSuffix());
    }

    @Override
    public String getOriginalKey(String redisKey) {
        checkNotNull(redisKey);
        String suffix = RedisKey.SEPARATOR + getSuffix();
        return redisKey.substring(0, redisKey.indexOf(suffix));
    }
    
    /**
     * If this key is being used in a shared environment (development or local, where multiple 
     * users are using the same Redis server), suffix the key with the user's name so these 
     * keys do not collide (particularly Travis and Heroku on development). Will not 
     * change/break keys in staging or production.
     * @param key
     * @return
     */
    private String keyForSharedRedis(String key) {
        if (SHARED_ENV.contains(config.getEnvironment())) {
            return key + RedisKey.SEPARATOR + config.getUser();
        }
        return key;
    }

}
