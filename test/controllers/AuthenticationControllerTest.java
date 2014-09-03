package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_NOT_FOUND;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.APPLICATION_JSON;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.SESSION_TOKEN;
import static org.sagebionetworks.bridge.TestConstants.SIGN_IN_URL;
import static org.sagebionetworks.bridge.TestConstants.SIGN_OUT_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USERNAME;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS;
import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationControllerTest {

    @Resource
    TestUserAdminHelper helper;
    
    @Before
    public void before() {
        helper.createOneUser();
    }
    
    @After
    public void after() {
        helper.deleteOneUser();
    }
    
    @Test
    public void signInNoCredentialsFailsWith404() {
        running(testServer(3333), new Runnable() {
            public void run() {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .post(node)
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates user not found", SC_NOT_FOUND, response.getStatus());
            }
        });
    }

    @Test
    public void signInGarbageCredentialsFailsWith404() {
        running(testServer(3333), new Runnable() {
            public void run() {
                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .post("username=bob&password=foo")
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates user not found", SC_NOT_FOUND, response.getStatus());
            }
        });
    }

    @Test
    public void signInBadCredentialsFailsWith404() {
        running(testServer(3333), new Runnable() {
            public void run() {
                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .setContentType(APPLICATION_JSON)
                        .post("{\"username\":\"bob\",\"password\":\"foo\"}")
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates user not found", SC_NOT_FOUND, response.getStatus());
            }
        });
    }

    @Test
    public void canSignIn() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, helper.getTestUser().getUsername());
                node.put(PASSWORD, helper.getTestUser().getPassword());

                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .post(node)
                        .get(TIMEOUT);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseNode = mapper.readTree(response.getBody());

                assertEquals("HTTP response indicates request OK", SC_OK, response.getStatus());
                String sessionToken = responseNode.get(SESSION_TOKEN).asText();
                assertNotNull("Session token is assigned", sessionToken);
                String username = responseNode.get(USERNAME).asText();
                assertEquals("Username is for test2 user", helper.getTestUser().getUsername(), username);
            }
        });
    }

    @Test
    public void canSignOut() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, helper.getTestUser().getUsername());
                node.put(PASSWORD, helper.getTestUser().getPassword());
                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL).post(node).get(TIMEOUT);
                
                WS.Cookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                assertTrue("Cookie is not empty", StringUtils.isNotBlank(cookie.getValue()));

                response = WS.url(TEST_BASE_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue()).get().get(TIMEOUT);

                cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                assertEquals("Cookie has been set to empty string", "", cookie.getValue());
            }
        });
    }
    
}
