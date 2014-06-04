package org.sagebionetworks.bridge.config;

public enum Environment {

    LOCAL("local"),
    DEV("dev"),
    PROD("prod");

    public String getEnvName() {
        return name;
    }

    private Environment(String name) {
        this.name = name;
    }

    private final String name;
}
