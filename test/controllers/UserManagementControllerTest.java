package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.SIGN_OUT_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USER_URL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUtils.FailableRunnable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserManagementControllerTest {

    @Resource
    private TestUserAdminHelper helper;

    private TestUser adminUser;

    @Before
    public void before() {
        adminUser = helper.createUser(UserManagementControllerTest.class, BridgeConstants.ADMIN_GROUP);
    }

    @After
    public void after() {
        helper.deleteUser(adminUser);
    }

    @Test
    @Ignore
    // This test sometimes succeeds, and sometimes fails with
    // java.util.concurrent.TimeoutException: Futures timed out after [10000 milliseconds].
    // The user is deleted in this case, there are no schedules to delete. This test is 
    // going to move to the SDK and we'll try again there.
    public void canCreateAndDeleteUser() {
        running(testServer(3333), new FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                String name = helper.makeRandomUserName(UserManagementControllerTest.class);
                String email = name + "@sagebridge.org";
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("username", name);
                node.put("email", email);
                node.put("password", "P4ssword");
                node.put("consent", true);
                
                Map<String,String> queryParams = new HashMap<String,String>();
                queryParams.put("email", email);

                try {
                    Response response = TestUtils.getURL(adminUser.getSessionToken(), USER_URL).post(node).get(TIMEOUT);
                    assertEquals("Response status is created.", SC_CREATED, response.getStatus());
                    
                    String sessionToken = response.asJson().get("sessionToken").asText();
                    TestUtils.getURL(sessionToken, SIGN_OUT_URL).get().get(TIMEOUT);
                    
                } finally {
                    Response response = TestUtils.getURL(adminUser.getSessionToken(), USER_URL, queryParams).delete().get(TIMEOUT);
                    assertEquals("Response status is OK.", SC_OK, response.getStatus());
                }
            }
        });
    }
}
