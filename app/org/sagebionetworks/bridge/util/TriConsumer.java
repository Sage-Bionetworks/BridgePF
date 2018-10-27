package org.sagebionetworks.bridge.util;

/** A 3-argument equivalent of {@link java.util.function.BiConsumer} */
@FunctionalInterface
public interface TriConsumer<T, U, V> {
    /** Performs this operation on the given arguments. */
    void accept(T t, U u, V v);
}
