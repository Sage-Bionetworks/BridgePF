package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.context.BridgeContext;

import com.amazonaws.auth.BasicAWSCredentials;

public class CredentialsFactory {
    
    public static BasicAWSCredentials getCredentials() {
        BridgeContext context = new BridgeContext();
        String key = context.getDecrypted("aws.key");
        String secretKey = context.getDecrypted("aws.secret.key");
        return new BasicAWSCredentials(key, secretKey);
    }

}
