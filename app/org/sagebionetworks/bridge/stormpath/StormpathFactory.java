package org.sagebionetworks.bridge.stormpath;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;

public class StormpathFactory {

    public static Client getStormpathClient() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        ApiKey apiKey = ApiKeys.builder()
            .setId(config.getStormpathId().trim())
            .setSecret(config.getStormpathSecret().trim()).build();
        return Clients.builder().setApiKey(apiKey).build();
    }

    public static Application getStormpathApplication(Client client) {
        BridgeConfig config = BridgeConfigFactory.getConfig();

        return client.getResource(config.getStormpathApplicationHref().trim(), Application.class);
    }
    
    public static String getApplicationId() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String url = config.getStormpathApplicationHref().trim();
        String[] parts = url.split("/");
        return parts[parts.length-1];
    }
    
}
