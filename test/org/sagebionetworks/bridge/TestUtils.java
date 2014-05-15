package org.sagebionetworks.bridge;

import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.RECORD_URL;
import static org.sagebionetworks.bridge.TestConstants.SIGN_IN_URL;
import static org.sagebionetworks.bridge.TestConstants.SIGN_OUT_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.TRACKER_URL;
import static org.sagebionetworks.bridge.TestConstants.USERNAME;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;
import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestUtils {

    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;
        @Override
        public void run() {
            try {
                testCode();
            } catch(Exception e) {
                fail(e.getMessage());
            }       
        }
    }

    public static String signIn() throws Exception {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(USERNAME, "test2");
        node.put(PASSWORD, PASSWORD);

        Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node).get(TIMEOUT);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(response.getBody());
        return responseNode.get("payload").get("sessionToken").asText();
    }

    public static WSRequestHolder getURL(String sessionToken, String path) {
        return WS.url(TEST_URL + path).setHeader("Bridge-Session", sessionToken);
    }
    
    public static void signOut() {
        WS.url(TEST_URL + SIGN_OUT_URL).get().get(TIMEOUT);
    }

    public static void deleteAllHealthData() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                
                Response response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                if (array.isArray()) {
                    for (int i=0; i < array.size(); i++) {
                        JsonNode child = array.get(i);
                        String recordId = child.get("recordId").asText();
                        response = getURL(sessionToken, RECORD_URL + recordId).delete().get(TIMEOUT);
                    }
                }
                
                response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                body = response.asJson();
                array = (ArrayNode)body.get("payload");
                
                signOut();
            }
        });
    }
}
