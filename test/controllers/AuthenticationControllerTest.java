package controllers;

import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.WS;
import play.libs.WS.Response;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

public class AuthenticationControllerTest {

    @Test
    public void signInNoCredentialsFailsWith400() {
        running(testServer(3333), new Runnable() {
            public void run() {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node)
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates bad request",
                        BAD_REQUEST, response.getStatus());
            }
        });
    }

    @Test
    public void signInGarbageCredentialsFailsWith400() {
        running(testServer(3333), new Runnable() {
            public void run() {
                Response response = WS.url(TEST_URL + SIGN_IN_URL)
                        .post("username=bob&password=foo").get(TIMEOUT);
                assertEquals("HTTP response indicates bad request",
                        BAD_REQUEST, response.getStatus());
            }
        });
    }

    @Test
    public void signInBadCredentialsFailsWith404() {
        running(testServer(3333), new Runnable() {
            public void run() {
                Response response = WS.url(TEST_URL + SIGN_IN_URL)
                        .setContentType(APPLICATION_JSON)
                        .post("{\"username\":\"bob\",\"password\":\"foo\"}")
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates user not found",
                        NOT_FOUND, response.getStatus());
            }
        });
    }

    @Test
    public void canSignIn() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, TEST2.USERNAME);
                node.put(PASSWORD, TEST2.PASSWORD);

                Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node)
                        .get(TIMEOUT);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseNode = mapper.readTree(response.getBody());

                assertEquals("HTTP response indicates request OK", OK,
                        response.getStatus());
                String sessionToken = responseNode.get(PAYLOAD)
                        .get(SESSION_TOKEN).asText();
                assertNotNull("Session token is assigned", sessionToken);
                String username = responseNode.get(PAYLOAD).get(USERNAME)
                        .asText();
                assertEquals("Username is for test2 user", TEST2.USERNAME,
                        username);
            }
        });
    }

    @Test
    public void canSignOut() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, TEST3.USERNAME);
                node.put(PASSWORD, TEST3.PASSWORD);
                Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node)
                        .get(TIMEOUT);
                WS.Cookie cookie = response
                        .getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                assertTrue("Cookie is not empty",
                        StringUtils.isNotBlank(cookie.getValue()));

                response = WS
                        .url(TEST_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN_HEADER,
                                cookie.getValue()).get().get(TIMEOUT);

                cookie = response
                        .getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                assertEquals("Cookie has been set to empty string", "",
                        cookie.getValue());
            }
        });
    }
}
