package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class StormpathUtilsTest {
    
    public static final String TEST_ENDPOINT = "https://webservices.sagebridge.org/api/v1/profile";
    
    @Test
    public void canGet() throws Exception {
        CloseableHttpClient client = getCloseableHttpClient();
        
        ObjectNode node = StormpathUtils.getJSON(client, TEST_ENDPOINT);
        // You don't get this error payload from Bridge unless you've successfully contacted the server.        
        assertEquals("Not signed in.", node.get("message").asText());
    }
    
    @Test
    public void canPost() throws Exception {
        CloseableHttpClient client = getCloseableHttpClient();
        
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node = StormpathUtils.postJSON(client, TEST_ENDPOINT, node);
        // You don't get this error payload from Bridge unless you've successfully contacted the server.        
        assertEquals("Not signed in.", node.get("message").asText());
    }

    private CloseableHttpClient getCloseableHttpClient() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(config.getStormpathId(), config.getStormpathSecret());
        provider.setCredentials(AuthScope.ANY, credentials);
        CloseableHttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        return client;
    }

}
