package controllers;
import java.util.concurrent.TimeUnit;

import org.junit.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.WS;
import play.libs.WS.Response;
import test.TestConstants;
import static org.fest.assertions.Assertions.*;
import static org.junit.Assert.fail;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;

public class AuthenticationTest {
	
	@Test
	public void signInNoCredentialsFailsWith500() {
		/* May not be loading the spring application context
		ObjectNode node = JsonNodeFactory.instance.objectNode();
		FakeRequest request = new FakeRequest().withJsonBody(node);
		
		Result result = callAction(controllers.routes.ref.Authentication.signIn(), request);
		assertThat(status(result)).isEqualTo(INTERNAL_SERVER_ERROR);
		*/
    	running(testServer(3333), new Runnable() {
			public void run() {
				ObjectNode node = JsonNodeFactory.instance.objectNode();
				Response response = WS.url(TestConstants.TEST_URL+"/api/auth/signIn").post(node).get(1L, TimeUnit.SECONDS);
				assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
			}
		});
		
	}
	@Test
	public void signInGarbageCredentialsFailsWith500() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				Response response = WS.url(TestConstants.TEST_URL+"/api/auth/signIn").post("username=bob&password=foo").get(1L, TimeUnit.SECONDS);
				assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
			}
		});
	}
	@Test
	public void signInBadCredentialsFailsWith401() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				Response response = WS.url(TestConstants.TEST_URL+"/api/auth/signIn").setContentType("application/json").post("{\"username\":\"bob\",\"password\":\"foo\"}").get(1L, TimeUnit.SECONDS);
				assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
			}
		});
	}
	
	@Ignore
	@Test
	public void canSignIn() {
    	running(testServer(3333), new FailableRunnable() {
			public void testCode() throws Exception {
				// TBD: Need a mock with credentials that will work. Right now this fails.
				ObjectNode node = JsonNodeFactory.instance.objectNode();
				node.put("username", "validname");
				node.put("password", "password");
				Response response = WS.url(TestConstants.TEST_URL+"/api/auth/signIn").post(node).get(1L, TimeUnit.SECONDS);
				
				ObjectMapper mapper = new ObjectMapper();
				JsonNode responseNode = mapper.readTree(response.getBody());
				
				String sessionToken = responseNode.get("sessionToken").asText();
				
				assertThat(response.getStatus()).isEqualTo(OK);
				assertThat(sessionToken).isNotNull();
			}
		});
	}
	
	@Test
	public void canSignOut() {
		// sign in
		// sign out
	}

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
	
}
