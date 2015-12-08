package org.sagebionetworks.bridge.util;

import java.util.stream.Collector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Adopted from the OpenGamma library (Apache License v2):
 * 
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 * 
 * @see https://github.com/OpenGamma/Strata/blob/master/modules/collect/src/main/java/com/opengamma/strata/collect/Guavate.java
 */
public class BridgeCollectors {
    
    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toImmutableList() {
        return Collector.of(
                ImmutableList.Builder<T>::new, 
                ImmutableList.Builder<T>::add, 
                (l, r) -> l.addAll(r.build()),
                ImmutableList.Builder<T>::build);
    }

    public static <T> Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> toImmutableSet() {
        return Collector.of(
                ImmutableSet.Builder<T>::new, 
                ImmutableSet.Builder<T>::add, 
                (l, r) -> l.addAll(r.build()),
                ImmutableSet.Builder<T>::build, 
                Collector.Characteristics.UNORDERED);
    }
}
