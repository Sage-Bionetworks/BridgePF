package org.sagebionetworks.bridge.redis;

abstract class AbstractRedisKey implements RedisKey {

    @Override
    public String getRedisKey(String key) {
        validate(key);
        return RedisKey.ENV_NAME + RedisKey.SEPARATOR + key + RedisKey.SEPARATOR + getSuffix();
    }

    @Override
    public String getOriginalKey(String redisKey) {
        if (redisKey == null || redisKey.isEmpty()) {
            throw new IllegalArgumentException("The redis key must not be null or empty.");
        }
        String suffix = RedisKey.SEPARATOR + getSuffix();
        return redisKey.substring(0, redisKey.indexOf(suffix));
    }

    void validate(String part) {
        if (part == null || part.isEmpty()) {
            throw new IllegalArgumentException("The supplied part must not be null or empty.");
        }
        if (part.contains(SEPARATOR)) {
            throw new IllegalArgumentException(SEPARATOR
                    + " is the reserved separator and must not be in the supplied key part.");
        }
    }
}
