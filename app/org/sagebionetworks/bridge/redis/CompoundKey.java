package org.sagebionetworks.bridge.redis;

/**
 * Keys of multiple suffixes (domains, name spaces).
 */
final class CompoundKey extends AbstractRedisKey {

    private final String suffix;

    CompoundKey(SimpleKey... keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("Keys must not be null or empty.");
        }
        StringBuilder suffixBuilder = new StringBuilder();
        // Loop backward
        for (int i = keys.length - 1; i >= 0; i--) {
            RedisKey key = keys[i];
            suffixBuilder.append(SEPARATOR);
            suffixBuilder.append(key.getSuffix());
        }
        // Remove the ':' at the beginning
        suffix = suffixBuilder.substring(1);
    }

    @Override
    public String getSuffix() {
        return suffix;
    }
}
