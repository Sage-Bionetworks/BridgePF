package org.sagebionetworks.bridge;

import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.*;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import org.sagebionetworks.bridge.models.User;

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
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    public static String signIn() throws Exception {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(USERNAME, TEST2.USERNAME);
        node.put(PASSWORD, TEST2.PASSWORD);

        Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node)
                .get(TIMEOUT);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(response.getBody());
        return responseNode.get(PAYLOAD).get("sessionToken").asText();
    }

    public static WSRequestHolder getURL(String sessionToken, String path) {
        return WS.url(TEST_URL + path).setHeader(
                BridgeConstants.SESSION_TOKEN_HEADER, sessionToken);
    }

    public static void signOut() {
        WS.url(TEST_URL + SIGN_OUT_URL).get().get(TIMEOUT);
    }

    public static void deleteAllHealthData() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();

                Response response = getURL(sessionToken, TRACKER_URL).get()
                        .get(TIMEOUT);

                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode) body.get(PAYLOAD);
                if (array.isArray()) {
                    for (int i = 0; i < array.size(); i++) {
                        JsonNode child = array.get(i);
                        String recordId = child.get(RECORD_ID).asText();
                        response = getURL(sessionToken, RECORD_URL + recordId)
                                .delete().get(TIMEOUT);
                    }
                }

                response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                body = response.asJson();
                array = (ArrayNode) body.get(PAYLOAD);

                signOut();
            }
        });
    }
    
    public static User constructTestUser(UserCredentials cred) {
        User user = new User();
        user.setEmail(cred.EMAIL);
        user.setUsername(cred.USERNAME);
        user.setFirstName(cred.FIRSTNAME);
        user.setLastName(cred.LASTNAME);
        user.setStormpathHref("<EMPTY>");
        
        return user;
    }
}
