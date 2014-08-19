package controllers;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Resource;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationServiceImpl;
import org.sagebionetworks.bridge.services.StormPathUserAdminService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import play.libs.WS.Response;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileControllerTest {

    private ObjectMapper mapper = new ObjectMapper();
    
    @Resource
    AuthenticationServiceImpl authService;

    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    StormPathUserAdminService userAdminService;

    private Study study;
    
    private TestUser testUser = new TestUser("test2User", "test2@sagebridge.org", "P4ssword");
    
    private UserSession adminUserSession;
    private UserSession userSession;

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    @Before
    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        TestUser admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        adminUserSession = authService.signIn(study, admin.getSignIn());
        
        userSession = userAdminService.createUser(adminUserSession.getUser(), testUser.getSignUp(), study, true, true);
    }
    
    @After
    public void after() {
        userAdminService.deleteUser(adminUserSession.getUser(), userSession.getUser(), study);
    }

    @Test
    public void getUserProfileWithEmptySessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL("", PROFILE_URL).get().get(TIMEOUT);

                assertEquals("HTTP Status will be 401", UNAUTHORIZED, response.getStatus());
            }
        });
    }
    
    @Test
    public void getUserProfileWithNullSessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(null, PROFILE_URL).get().get(TIMEOUT);

                assertEquals("HTTP Status will be 401", UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void getUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(userSession.getSessionToken(), PROFILE_URL).get().get(TIMEOUT);

                int count = 0;
                
                List<String> profileFieldNames = Lists.newArrayList("firstName", "lastName", "username", "email");
                Iterator<Entry<String, JsonNode>> fields = response.asJson().fields();
                while (fields.hasNext()) {
                    String fieldName = fields.next().getKey();
                    if (profileFieldNames.contains(fieldName)) {
                        count++;
                    }
                }
                assertEquals("User profile has all required fields.", count, 4);
            }
        });
    }

    @Test
    public void updateUserProfileWithEmptySessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                UserProfile user = new TestUser("tester", "tester@sagebase.org", "tester").getUserProfile("1234");
                Response response = TestUtils.getURL("", PROFILE_URL).post(mapper.writeValueAsString(user))
                        .get(TIMEOUT);

                assertEquals("HTTP Status should be 401", UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void updateUserProfileWithNullSessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                UserProfile user = new TestUser("tester", "tester@sagebase.org", "tester").getUserProfile("1234");
                Response response = TestUtils.getURL(null, PROFILE_URL).post(mapper.writeValueAsString(user))
                        .get(TIMEOUT);

                assertEquals("HTTP Status should be 401", UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void updateUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                UserProfile user = new TestUser("tester", "tester@sagebase.org", "tester").getUserProfile("1234");
                Response response = TestUtils.getURL(userSession.getSessionToken(), PROFILE_URL)
                        .post(mapper.writeValueAsString(user)).get(TIMEOUT);

                assertEquals("HTTP Status should be 200 OK", OK, response.getStatus());
            }
        });
    }
}
