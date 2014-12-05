package org.sagebionetworks.bridge.redis;

public interface IntegerOps {

    RedisOp<Long> increment(String key);
    
    RedisOp<Long> decrement(String key);
    
    RedisOp<String> set(String key, long value);
    
    RedisOp<String> setex(String key, int seconds, long value);
    
    RedisOp<Long> get(String key);
    
    RedisOp<Long> delete(String key);
    
}
