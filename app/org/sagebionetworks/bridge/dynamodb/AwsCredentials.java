package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.config.BridgeConfig;

import com.amazonaws.auth.BasicAWSCredentials;

public class AwsCredentials {

    @Resource(name="bridgeConfig")
    private BridgeConfig bridgeConfig;

    public BasicAWSCredentials getCredentials() {
        String key = bridgeConfig.getProperty("aws.key");
        String secretKey = bridgeConfig.getProperty("aws.secret.key");
        return new BasicAWSCredentials(key, secretKey);
    }
}
