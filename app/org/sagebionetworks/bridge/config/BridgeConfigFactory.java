package org.sagebionetworks.bridge.config;

public class BridgeConfigFactory {
    private static final BridgeConfig INSTANCE = new BridgeConfig();
    public static BridgeConfig getConfig() {
        return INSTANCE;
    }
}
