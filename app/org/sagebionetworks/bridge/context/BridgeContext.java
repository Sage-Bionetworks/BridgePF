package org.sagebionetworks.bridge.context;

import java.util.HashMap;
import java.util.Map;

import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeContext {

    private static final Logger logger = LoggerFactory.getLogger(BridgeContext.class);

    public static final String PASSWORD = "pwd";
	public static final String ENVIRONMENT = "environment";
	
	private final Map<String, String> nameValueMap = new HashMap<String, String>();
	private final PBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

	public BridgeContext() {
	    nameValueMap.put(PASSWORD, "");
        nameValueMap.put(ENVIRONMENT, "stub");
        // TODO: Have a properties file or files, switched by environment.
        nameValueMap.put("aws.key", "XszaP+EsOz1dVz9P5TTuaabZoOR6KYC5O46IbJy/9bY=");
        nameValueMap.put("aws.secret.key", "wtQuhjk8qxLofjgmkW+TgB0ZHO/V5sDx4Qm1PxiAdawBC9BVJ0aTqOb+kfnfz+zUrJqwlg72doU=");
        readEnv();
        readSystemProperties();
        
        String pwd = nameValueMap.get(PASSWORD);
        if (pwd == null || pwd.isEmpty()) {
            logger.warn("Missing decryptor password.");
        } else {
            encryptor.setPassword(pwd);
        }
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
	
	public String getDecrypted(String key) {
	    return encryptor.decrypt(nameValueMap.get(key));
	}
    
    public String encryptValue(String value) {
        return encryptor.encrypt(value);
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
