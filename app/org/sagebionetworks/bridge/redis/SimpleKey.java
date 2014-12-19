package org.sagebionetworks.bridge.redis;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Single suffix (domain, name space) key.
 */
final class SimpleKey extends AbstractRedisKey {

    private final String name;

    SimpleKey(String name) {
        checkNotNull(name);
        checkArgument(!name.contains(SEPARATOR));
        this.name = name;
    }

    @Override
    public String getSuffix() {
        return name;
    }
}
