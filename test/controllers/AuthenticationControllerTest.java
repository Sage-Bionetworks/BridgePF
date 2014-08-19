package controllers;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationServiceImpl;
import org.sagebionetworks.bridge.services.StormPathUserAdminService;
import org.sagebionetworks.bridge.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationControllerTest {

    private static TestUser testUser = new TestUser("test", "tester@sagebridge.org", "P4ssword");
    private Study study;
    private User adminUser; 
    private User user;
    
    @Resource
    AuthenticationServiceImpl authService;
    
    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    StormPathUserAdminService userAdminService;

    @Before
    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        TestUser admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminUser = authService.signIn(study, admin.getSignIn()).getUser();

        UserSession session = userAdminService.createUser(adminUser, testUser.getSignUp(), study, true, true);
        user = session.getUser();
        authService.signOut(session.getSessionToken());
    }
    
    @After
    public void after() {
        if (user != null) {
            userAdminService.deleteUser(adminUser, user, study);    
        }
    }
    
    @Test
    public void signInNoCredentialsFailsWith404() {
        running(testServer(3333), new Runnable() {
            public void run() {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .post(node)
                        .get(TIMEOUT);
                assertEquals("HTTP response indicates user not found", NOT_FOUND, response.getStatus());
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
                assertEquals("HTTP response indicates user not found", NOT_FOUND, response.getStatus());
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
                assertEquals("HTTP response indicates user not found", NOT_FOUND, response.getStatus());
            }
        });
    }

    @Test
    public void canSignIn() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, testUser.getUsername());
                node.put(PASSWORD, testUser.getPassword());

                Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL)
                        .post(node)
                        .get(TIMEOUT);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseNode = mapper.readTree(response.getBody());

                assertEquals("HTTP response indicates request OK", OK, response.getStatus());
                String sessionToken = responseNode.get(SESSION_TOKEN).asText();
                assertNotNull("Session token is assigned", sessionToken);
                String username = responseNode.get(USERNAME).asText();
                assertEquals("Username is for test2 user", testUser.getUsername(), username);
            }
        });
    }

    @Test
    public void canSignOut() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, testUser.getUsername());
                node.put(PASSWORD, testUser.getPassword());
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
