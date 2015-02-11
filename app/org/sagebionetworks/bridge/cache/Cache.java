package org.sagebionetworks.bridge.cache;

import java.util.List;

public interface Cache {
    <T> T get(Class<? extends T> clazz, String key);

    <T> List<T> getList(Class<? extends T> clazz, String key);

    <T> void put(Class<? extends T> clazz, String key, T value, int ttlSeconds);

    <T> void putList(Class<? extends T> clazz, String key, List<T> valueList, int ttlSeconds);

    void remove(Class<?> clazz, String key);

    void removeList(Class<?> clazz, String key);
}
