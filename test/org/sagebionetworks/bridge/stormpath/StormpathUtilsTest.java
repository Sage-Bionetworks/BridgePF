package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;

import java.util.Map;

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
import com.google.common.collect.Maps;

public class StormpathUtilsTest {
    
    public static final String TEST_ENDPOINT = "https://webservices.sagebridge.org/api/v1/profile";

    @Test
    public void templateResolverWorks() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", "Belgium");
        map.put("box", "Albuquerque");
        map.put("foo", "This is unused");
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = StormpathUtils.resolveTemplate("foo ${baz} bar ${baz} ${box} ${unused}", map);
        assertEquals("foo Belgium bar Belgium Albuquerque ${unused}", result);
    }
    
    @Test
    public void templateResolverHandlesSomeJunkValues() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", null);
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = StormpathUtils.resolveTemplate("foo ${baz}", map);
        assertEquals("foo ${baz}", result);
        
        result = StormpathUtils.resolveTemplate(" ", map);
        assertEquals(" ", result);
    }
    
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
