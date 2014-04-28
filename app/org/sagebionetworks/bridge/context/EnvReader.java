package org.sagebionetworks.bridge.context;

public class EnvReader implements ContextReader {
    @Override
    public String read(String name) {
        return System.getenv(name);
    }
}
