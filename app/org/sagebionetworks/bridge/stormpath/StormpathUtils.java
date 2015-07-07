package org.sagebionetworks.bridge.stormpath;

import java.io.IOException;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class StormpathUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(StormpathUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Used for the Stormpath REST API. This should go away when they update their SDK.
     * @param client
     * @param url
     * @param node
     * @return
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    static ObjectNode getJSON(CloseableHttpClient client, String url) throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "application/json");
        try(CloseableHttpResponse response = client.execute(get)) {
            String bodyString = EntityUtils.toString(response.getEntity(), "UTF-8");
            if (logger.isDebugEnabled()) {
                logger.debug("GET: " + url + "\n   request: <EMPTY>\n   response: "+ bodyString);
            }
            return (ObjectNode)MAPPER.readTree(bodyString);
        }
    }
    
    /**
     * Used for the Stormpath REST API. This should go away when they update their SDK.
     * @param client
     * @param url
     * @param requestNode
     * @return
     * @throws UnsupportedCharsetException 
     * @throws IOException 
     * @throws ClientProtocolException 
     * @throws Exception
     */
    static ObjectNode postJSON(CloseableHttpClient client, String url, ObjectNode requestNode) 
                    throws UnsupportedCharsetException, ClientProtocolException, IOException {
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(MAPPER.writeValueAsString(requestNode), "UTF-8"));
        
        try (CloseableHttpResponse response = client.execute(post)) {
            String bodyString = EntityUtils.toString(response.getEntity(), "UTF-8");
            if (logger.isDebugEnabled()) {
                logger.debug("POST: " + url + "\n   request: "+MAPPER.writeValueAsString(requestNode)+"\n   response: " + bodyString);    
            }
            return (ObjectNode)MAPPER.readTree(bodyString);
        }
    }
    
}
