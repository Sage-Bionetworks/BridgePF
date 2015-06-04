package org.sagebionetworks.bridge.redis;

import javax.annotation.Nonnull;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.Pool;

/** Static utility functions for Jedis. */
public class JedisUtil {
    /**
     * Closes a Jedis connection. This encapsulates logic to suppress the "Could not return the resource to the pool"
     * exceptions, which is a known bug. See https://github.com/xetorthio/jedis/issues/992
     */
    public static void closeJedisConnection(@Nonnull Jedis jedis) {
        try {
            jedis.close();
        } catch (JedisException e) {
            // Jedis throws an exception here on closed connections
            // See https://github.com/xetorthio/jedis/issues/992
            // We skip this particular exception
            final String message = e.getMessage();
            if (message != null && message.equals(
                    "Could not return the resource to the pool")) {
                StackTraceElement[] stackTrace = e.getStackTrace();
                if (stackTrace != null && stackTrace.length > 0) {
                    final StackTraceElement topFrame = stackTrace[0];
                    final String className = topFrame.getClassName();
                    if (className.equals(Pool.class.getName()) ||
                            className.equals(JedisPool.class.getName())) {
                        return;
                    }
                }
            }
            throw e;
        }
    }
}
