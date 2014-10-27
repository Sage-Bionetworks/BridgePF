package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.apache.commons.httpclient.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.TestConstants.PROFILE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileControllerTest {

    private ObjectMapper mapper = new ObjectMapper();
    
    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    @Before
    public void before() {
        testUser = helper.createUser(UserProfileControllerTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test
    public void getUserProfileWithNullSessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(null, PROFILE_URL).get().get(TIMEOUT);
                assertEquals("HTTP Status will be 401", SC_UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void getUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(testUser.getSessionToken(), PROFILE_URL).get().get(TIMEOUT);

                JsonNode node = response.asJson();
                assertNotNull("First name exists", node.get("firstName"));
                assertNotNull("First name exists", node.get("lastName"));
                assertNotNull("First name exists", node.get("username"));
                assertNotNull("First name exists", node.get("email"));
                assertEquals("Is type UserProfile", "UserProfile", node.get("type").asText());
            }
        });
    }

    @Test
    public void updateUserProfileWithEmptySessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL("", PROFILE_URL)
                        .post(mapper.writeValueAsString(testUser.getSessionToken())).get(TIMEOUT);

                assertEquals("HTTP Status should be 401", SC_UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void updateUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(testUser.getSessionToken(), PROFILE_URL)
                        .post(mapper.writeValueAsString(testUser.getUser())).get(TIMEOUT);

                assertEquals("HTTP Status should be 200 OK", SC_OK, response.getStatus());
            }
        });
    }
}
