package org.sagebionetworks.bridge.context;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeContext {
	
	private static final String ENVIRONMENT = "environment";

	private Logger logger = LoggerFactory.getLogger(BridgeContext.class);
	
	private final Map<String, String> nameValueMap = new HashMap<String, String>();
	 
	public BridgeContext() {
		nameValueMap.put(ENVIRONMENT, "development");
		readEnv();
        readSystemProperties();
	}

	public String getEnvironment() {
		return nameValueMap.get(ENVIRONMENT);
	}
	
	public boolean isLocal() {
		return "local".equals(nameValueMap.get(ENVIRONMENT));
	}
	
	public boolean isStub() {
		return "stub".equals(nameValueMap.get(ENVIRONMENT));
	}
	
	public boolean isDevelopment() {
		return "development".equals(nameValueMap.get(ENVIRONMENT));
	}
	
	public String get(String key) {
		return nameValueMap.get(key);
	}
	
	private void readEnv() {
        try {
            populateNameValueMap(new EnvReader());
        } catch (SecurityException e) {
            logger.info("Cannot read environment variables because of SecurityException.");
            return;
        }
    }

    private void readSystemProperties() {
        try {
            populateNameValueMap(new SystemPropertyReader());
        } catch (SecurityException e) {
            logger.info("Cannot read system properties because of SecurityException.");
        }
    }

    private void populateNameValueMap(ContextReader reader) {
        for (String key : nameValueMap.keySet()) {
        	String val = reader.read(key);
        	if (val != null) {
        		nameValueMap.put(key, val);
            }
        }
    }
}
