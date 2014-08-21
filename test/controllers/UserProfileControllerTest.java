package controllers;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Resource;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
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

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileControllerTest {

    private ObjectMapper mapper = new ObjectMapper();
    
    @Resource
    TestUserAdminHelper helper;

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    @Before
    public void before() {
        helper.createOneUser();
    }
    
    @After
    public void after() {
        helper.deleteOneUser();
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
                Response response = TestUtils.getURL(helper.getUserSessionToken(), PROFILE_URL).get().get(TIMEOUT);

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
                Response response = TestUtils.getURL("", PROFILE_URL)
                        .post(mapper.writeValueAsString(helper.getUserSessionToken())).get(TIMEOUT);

                assertEquals("HTTP Status should be 401", UNAUTHORIZED, response.getStatus());
            }
        });
    }

    @Test
    public void updateUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(helper.getUserSessionToken(), PROFILE_URL)
                        .post(mapper.writeValueAsString(helper.getUser())).get(TIMEOUT);

                assertEquals("HTTP Status should be 200 OK", OK, response.getStatus());
            }
        });
    }
}
