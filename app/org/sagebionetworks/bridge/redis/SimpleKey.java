package org.sagebionetworks.bridge.redis;

class SimpleKey extends AbstractRedisKey {

    private final String name;

    SimpleKey(String name) {
        validate(name);
        this.name = name;
    }

    @Override
    public String getSuffix() {
        return name;
    }
}
