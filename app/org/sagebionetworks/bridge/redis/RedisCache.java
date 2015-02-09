package org.sagebionetworks.bridge.redis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.Cache;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

@Component
public class RedisCache implements Cache {
    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);

    private static final String KEY_PATTERN = "%s:%s";
    private static final String LIST_KEY_PATTERN = "List:%s:%s";

    private JedisStringOps stringOps;

    @Autowired
    public void setStringOps(JedisStringOps stringOps) {
        this.stringOps = stringOps;
    }

    @Override
    public <T> T get(Class<? extends T> clazz, String key) {
        String redisKey = String.format(KEY_PATTERN, clazz.getName(), key);

        try {
            String json = stringOps.get(redisKey);
            if (json != null) {
                return BridgeObjectMapper.get().readValue(json, clazz);
            } else {
                return null;
            }
        } catch (IOException | RuntimeException ex) {
            logger.error(String.format("Error getting redis object for key %s", redisKey), ex);
            return null;
        }
    }

    @Override
    public <T> List<T> getList(Class<? extends T> clazz, String key) {
        String redisKey = String.format(LIST_KEY_PATTERN, clazz.getName(), key);

        try {
            String json = stringOps.get(redisKey);
            if (json == null) {
                return null;
            }

            JsonNode jsonNode = BridgeObjectMapper.get().readTree(json);
            if (!jsonNode.isArray()) {
                logger.error(String.format("Redis object for key %s is not a list", redisKey));
                return null;
            }

            List<T> resultList = new ArrayList<>();
            for (JsonNode oneJsonChild : jsonNode) {
                T oneResult = BridgeObjectMapper.get().convertValue(oneJsonChild, clazz);
                resultList.add(oneResult);
            }

            return resultList;
        } catch (IOException | RuntimeException ex) {
            logger.error(String.format("Error getting redis list object for key %s", redisKey), ex);
            return null;
        }
    }

    @Override
    public <T> void put(Class<? extends T> clazz, String key, T value, int ttlSeconds) {
        String redisKey = String.format(KEY_PATTERN, clazz.getName(), key);
        putInternal(redisKey, value, ttlSeconds);
    }

    @Override
    public <T> void putList(Class<? extends T> clazz, String key, List<T> valueList, int ttlSeconds) {
        String redisKey = String.format(LIST_KEY_PATTERN, clazz.getName(), key);
        putInternal(redisKey, valueList, ttlSeconds);
    }

    private <T> void putInternal(String internalKey, T value, int ttlSeconds) {
        try {
            String json = BridgeObjectMapper.get().writeValueAsString(value);
            String result = stringOps.setex(internalKey, ttlSeconds, json);
            if (!"OK".equals(result)) {
                logger.error(String.format("Error putting redis object for key %s, result code %s", internalKey, result));
            }
        } catch (JsonProcessingException | RuntimeException ex) {
            logger.error(String.format("Error putting redis object for key %s", internalKey), ex);
        }
    }

    @Override
    public void remove(Class<?> clazz, String key) {
        String redisKey = String.format(KEY_PATTERN, clazz.getName(), key);
        removeInternal(redisKey);
    }

    @Override
    public void removeList(Class<?> clazz, String key) {
        String redisKey = String.format(LIST_KEY_PATTERN, clazz.getName(), key);
        removeInternal(redisKey);
    }

    private void removeInternal(String internalKey) {
        try {
            stringOps.delete(internalKey);
        } catch(RuntimeException ex) {
            logger.error(String.format("Error deleting redis object for key %s", internalKey), ex);
        }
    }
}
