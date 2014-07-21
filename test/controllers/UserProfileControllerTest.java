package controllers;

import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.User;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import play.Logger;
import play.libs.WS.Response;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

//@ContextConfiguration("file:conf/application-context.xml")
//@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileControllerTest {
    
    private ObjectMapper mapper = new ObjectMapper();

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    private User constructTestUser() {
        User user = new User();
        user.setEmail(TEST1.EMAIL);
        user.setUsername(TEST1.USERNAME);
        user.setFirstName("foo");
        user.setLastName("bar");
        
        return user;
    }
    
    private boolean areUsersEqual(User a, JsonNode b) {
//        if (       a.getFirstName().equals(b.get("firstName"))
//                && a.getLastName() .equals(b.get("lastName"))
//                && a.getEmail()    .equals(b.get("email")))
//            return true;
        
        Logger.info(a.getFirstName() + " " + b.get("firstName"));
        Logger.info(a.getLastName() + " " + b.get("lastName"));
        Logger.info(a.getEmail() + " " + b.get("email"));
        Logger.info(a.getUsername() + " " + b.get("username"));
        
        return false;
    }
    
//    @Test
//    public void getUserProfileWithNoSessionFails() {
//        running(testServer(3333), new TestUtils.FailableRunnable() {
//            
//            @Override
//            public void testCode() throws Exception {
//                Response response = TestUtils.getURL(null, PROFILE_URL).get().get(TIMEOUT);
//                assertEquals("HTTP Status will be 500", INTERNAL_SERVER_ERROR, response.getStatus());
//            }
//        });
//    }
//    
//    @Test
//    public void getUserProfileSuccess() {
//        running(testServer(3333), new TestUtils.FailableRunnable() {
//            
//            @Override
//            public void testCode() throws Exception {
//                String sessionToken = TestUtils.signIn();
//                Response response = TestUtils.getURL(sessionToken, PROFILE_URL).get().get(TIMEOUT);
//                JsonNode payload = response.asJson().get("payload");
//                
//                int count = 0;
//                Iterator<Entry<String, JsonNode>> fields = payload.fields();
//                while (fields.hasNext()) {
//                    fields.next();
//                    count++;
//                }
//                
//                assertEquals("User profile has 5 fields.", count, 5);
//                
//                TestUtils.signOut();
//            }
//        });
//    }
//    
//    @Test
//    public void updateUserProfileWithNoSession() {
//        running(testServer(3333), new TestUtils.FailableRunnable() {
//            
//            @Override
//            public void testCode() throws Exception {
//                User user = constructTestUser();
//                Response response = TestUtils.getURL(null, UPDATE_URL).put(mapper.writeValueAsString(user))
//                        .get(TIMEOUT);
//                assertEquals("HTTP Status should be 500", INTERNAL_SERVER_ERROR, response.getStatus());
//            }
//        });
//    }
    
    @Test
    public void updateUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                User user = constructTestUser();
                Response response1 = TestUtils.getURL(sessionToken, UPDATE_URL).put(mapper.writeValueAsString(user))
                        .get(TIMEOUT);
                Logger.info(response1.getStatusText());
                assertEquals("HTTP Status should be 200 OK", OK, response1.getStatus());

                Response response2 = TestUtils.getURL(sessionToken, PROFILE_URL).get().get(TIMEOUT);
                JsonNode updatedUser = response2.asJson().get("payload");
                
                assertTrue("updateUserProfile successfully updates profile.", areUsersEqual(user, updatedUser));

                TestUtils.signOut();
            }
        });
    }
}
