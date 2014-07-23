package controllers;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.UserProfile;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.libs.WS.Response;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

public class UserProfileControllerTest {

    private ObjectMapper mapper = new ObjectMapper();

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
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
                String sessionToken = TestUtils.signIn();

                Response response = TestUtils.getURL(sessionToken, PROFILE_URL).get().get(TIMEOUT);
                JsonNode payload = response.asJson().get("payload");

                int count = 0;
                List<String> profileFieldNames = TestUtils.getUserProfileFieldNames();
                Iterator<Entry<String, JsonNode>> payloadFields = payload.fields();
                while (payloadFields.hasNext()) {
                    String payloadFieldName = payloadFields.next().getKey();
                    if (profileFieldNames.contains(payloadFieldName)) {
                        count++;
                    }
                }

                assertEquals("User profile has all required fields.", count, 5);

                TestUtils.signOut();
            }
        });
    }

    @Test
    public void updateUserProfileWithEmptySessionFails401() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                UserProfile user = TestUtils.constructTestUser(TEST1);
                Response response = TestUtils.getURL("", PROFILE_URL)
                                        .post(mapper.writeValueAsString(user))
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
                UserProfile user = TestUtils.constructTestUser(TEST1);
                Response response = TestUtils.getURL(null, PROFILE_URL)
                                        .post(mapper.writeValueAsString(user))
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
                String sessionToken = TestUtils.signIn();

                UserProfile user = TestUtils.constructTestUser(TEST1);
                Response response = TestUtils.getURL(sessionToken, PROFILE_URL)
                                        .post(mapper.writeValueAsString(user))
                                        .get(TIMEOUT);

                assertEquals("HTTP Status should be 200 OK", OK, response.getStatus());

                TestUtils.signOut();
            }
        });
    }
}
