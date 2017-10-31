package org.sagebionetworks.bridge.models;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Common operating system names. The operating system name is not constrained (any value could be used as long as the
 * study supports it), so this is not an enumeration.
 */
public class OperatingSystem {
    
    public static final String IOS = "iPhone OS";
    public static final String ANDROID = "Android";
    public static final String UNIVERSAL = "Universal";

    /**
     * Apple has changed the name of the iOS platform from "iPhone OS" to "iOS" and this is reflected in the 
     * User-Agent string we send. To avoid confusion, recognize such synonyms/spelling errors and map them
     * to our two canonical platforms, "iPhone OS" and "Android". 
     */
    public static final Map<String, String> SYNONYMS = new ImmutableMap.Builder<String, String>()
            .put("iOS", "iPhone OS").build();
    
    public static final Set<String> ALL_OS_SYSTEMS = new ImmutableSet.Builder<String>()
            .add(IOS).add(ANDROID).build();
}
