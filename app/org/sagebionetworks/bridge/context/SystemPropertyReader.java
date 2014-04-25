package org.sagebionetworks.bridge.context;

public class SystemPropertyReader implements ContextReader {

    @Override
    public String read(String name) {
        return System.getProperty(name);
    }
    
}
