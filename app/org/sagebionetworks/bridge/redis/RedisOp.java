package org.sagebionetworks.bridge.redis;

/**
 * Redis operation that wraps around one or more Redis commands.
 */
public interface RedisOp<T> {

    T execute();
}
