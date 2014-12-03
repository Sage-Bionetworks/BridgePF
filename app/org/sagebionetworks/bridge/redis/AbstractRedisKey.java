package org.sagebionetworks.bridge.redis;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class AbstractRedisKey implements RedisKey {

    @Override
    public String getRedisKey(String key) {
        checkNotNull(key);
        return key + RedisKey.SEPARATOR + getSuffix();
    }

    @Override
    public String getOriginalKey(String redisKey) {
        checkNotNull(redisKey);
        String suffix = RedisKey.SEPARATOR + getSuffix();
        return redisKey.substring(0, redisKey.indexOf(suffix));
    }
}
