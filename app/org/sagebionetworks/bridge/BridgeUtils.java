package org.sagebionetworks.bridge;

import java.util.UUID;

public class BridgeUtils {

    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
    
}
