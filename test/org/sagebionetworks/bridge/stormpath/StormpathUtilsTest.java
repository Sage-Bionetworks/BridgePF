package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class StormpathUtilsTest {
    
    public static final String TEST_ENDPOINT = "https://enterprise.stormpath.io/v1/directories/4MFGMk7w7My1SvN0ciqlW";
    
    @Test
    public void canGet() throws Exception {
        CloseableHttpClient client = getCloseableHttpClient();
        
        ObjectNode node = StormpathUtils.getJSON(client, TEST_ENDPOINT);
        assertEquals("Authentication required.", node.get("message").asText());
    }
    
    @Test
    public void canPost() throws Exception {
        CloseableHttpClient client = getCloseableHttpClient();
        
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node = StormpathUtils.postJSON(client, TEST_ENDPOINT, node);
        assertEquals("Authentication required.", node.get("message").asText());
    }

    private CloseableHttpClient getCloseableHttpClient() {
        CredentialsProvider provider = new BasicCredentialsProvider();
        CloseableHttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        return client;
    }

}
