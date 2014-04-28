package controllers;

import org.junit.*;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.WS;
import play.libs.WS.Response;
import static org.fest.assertions.Assertions.*;
import static org.junit.Assert.fail;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;

public class AuthenticationTest {
	
    private static final String SIGN_OUT_URL = "/api/auth/signOut";
    private static final String SIGN_IN_URL = "/api/auth/signIn";
    private static final String APPLICATION_JSON = "application/json";
    private static final String PASSWORD = "password";
    private static final String USERNAME = "username";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String PAYLOAD = "payload";

    private abstract class FailableRunnable implements Runnable {
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

	@Test
	public void signInNoCredentialsFailsWith500() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                Response response = WS.url(TestConstants.TEST_URL + SIGN_IN_URL).post(node)
                        .get(TestConstants.REQUEST_TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
			}
		});
		
	}
	@Test
	public void signInGarbageCredentialsFailsWith500() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                Response response = WS.url(TestConstants.TEST_URL + SIGN_IN_URL).post("username=bob&password=foo")
                        .get(TestConstants.REQUEST_TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
			}
		});
	}
	@Test
	public void signInBadCredentialsFailsWith404() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                Response response = WS.url(TestConstants.TEST_URL + SIGN_IN_URL).setContentType(APPLICATION_JSON)
                        .post("{\"username\":\"bob\",\"password\":\"foo\"}").get(TestConstants.REQUEST_TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
			}
		});
	}
	@Test
	public void canSignIn() {
    	running(testServer(3333), new FailableRunnable() {
			public void testCode() throws Exception {
				ObjectNode node = JsonNodeFactory.instance.objectNode();
				node.put(USERNAME, "test2");
				node.put(PASSWORD, PASSWORD);

                Response response = WS.url(TestConstants.TEST_URL + SIGN_IN_URL).post(node)
                        .get(TestConstants.REQUEST_TIMEOUT);
				ObjectMapper mapper = new ObjectMapper();
				JsonNode responseNode = mapper.readTree(response.getBody());
				
				assertThat(response.getStatus()).isEqualTo(OK);
                String sessionToken = responseNode.get(PAYLOAD).get(SESSION_TOKEN).asText();
				assertThat(sessionToken).isNotNull();
				String username = responseNode.get(PAYLOAD).get(USERNAME).asText();
				assertThat(username).isEqualTo("test2");
			}
		});
	}
	@Test
	public void canSignOut() {
	    running(testServer(3333), new FailableRunnable() {
	        public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, "test3");
                node.put(PASSWORD, PASSWORD);
                Response response = WS.url(TestConstants.TEST_URL+SIGN_IN_URL).post(node).get(TestConstants.REQUEST_TIMEOUT);
                WS.Cookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN);
                assertThat(cookie.getValue()).isNotEmpty();
                
                response = WS.url(TestConstants.TEST_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN, cookie.getValue()).get().get(TestConstants.REQUEST_TIMEOUT);
                
                cookie = response.getCookie(BridgeConstants.SESSION_TOKEN);
                assertThat(cookie.getValue()).isEqualTo("");
	        }
	    });
	}
}
