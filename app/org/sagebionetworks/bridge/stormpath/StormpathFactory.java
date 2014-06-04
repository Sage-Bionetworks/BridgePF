package org.sagebionetworks.bridge.stormpath;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.ApiKey;
import com.stormpath.sdk.client.ApiKeys;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;

public class StormpathFactory {

    public static Client createStormpathClient() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        ApiKey apiKey = ApiKeys.builder()
            .setId(config.getStormpathId())
            .setSecret(config.getStormpathSecret()).build();
        return Clients.builder().setApiKey(apiKey).build();
    }

    public static Application createStormpathApplication(Client client) {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        return client.getResource(config.getStormpathApplicationHref(), Application.class);
    }
}
