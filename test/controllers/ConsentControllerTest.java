package controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentControllerTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        testUser = helper.createUser(ConsentControllerTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test
    public void methodsAreDeprecated() {
        // This is expensive so test them all after starting the server, in one test
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("scope", "no_sharing");
                
                // First, verify this header isn't on *every* endpoint
                WSRequestHolder holder = WS.url(TEST_BASE_URL + "/api/v1/consent/email");
                Response response = holder.post(node).get(TIMEOUT);
                String headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertNull(headerValue);
                
                // Now verify it's on the intended endpoints
                holder = WS.url(TEST_BASE_URL + "/api/v1/consent/dataSharing/suspend");
                response = holder.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals("deprecated", headerValue);
                
                holder = WS.url(TEST_BASE_URL + "/api/v1/consent/dataSharing/resume");
                response = holder.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals("deprecated", headerValue);
                
                holder = WS.url(TEST_BASE_URL + "/api/v1/consent");
                response = holder.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals("deprecated", headerValue);
                
            }
        });
    }
  
}
