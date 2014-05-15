package controllers;

import org.junit.*;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.WS;
import play.libs.WS.Response;
import static org.fest.assertions.Assertions.*;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;

public class AuthenticationControllerTest {
	
	@Test
	public void signInNoCredentialsFailsWith400() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node).get(REQUEST_TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
			}
		});
	}
	@Test
	public void signInGarbageCredentialsFailsWith400() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                Response response = WS.url(TEST_URL + SIGN_IN_URL).post("username=bob&password=foo")
                        .get(REQUEST_TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
			}
		});
	}
	@Test
	public void signInBadCredentialsFailsWith404() {
    	running(testServer(3333), new Runnable() {
			public void run() {
                Response response = WS.url(TEST_URL + SIGN_IN_URL).setContentType(APPLICATION_JSON)
                        .post("{\"username\":\"bob\",\"password\":\"foo\"}").get(TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
			}
		});
	}
	@Test
	public void canSignIn() {
    	running(testServer(3333), new TestUtils.FailableRunnable() {
			public void testCode() throws Exception {
				ObjectNode node = JsonNodeFactory.instance.objectNode();
				node.put(USERNAME, "test2");
				node.put(PASSWORD, PASSWORD);

                Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node)
                        .get(REQUEST_TIMEOUT);
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
	    running(testServer(3333), new TestUtils.FailableRunnable() {
	        public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, "test3");
                node.put(PASSWORD, PASSWORD);
                Response response = WS.url(TEST_URL+SIGN_IN_URL).post(node).get(TIMEOUT);
                WS.Cookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);
                assertThat(cookie.getValue()).isNotEmpty();
                
                response = WS.url(TEST_URL + SIGN_OUT_URL).setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue())
                        .get().get(TIMEOUT);
                
                cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);
                assertThat(cookie.getValue()).isEqualTo("");
	        }
	    });
	}
}
