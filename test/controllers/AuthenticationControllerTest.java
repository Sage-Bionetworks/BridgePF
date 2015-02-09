package controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
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
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationControllerTest {

    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        testUser = helper.createUser(AuthenticationControllerTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    // This test is easiest to do here, where we can verify in Redis the session has been destroyed.
    @Test
    public void canSignOut() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(USERNAME, testUser.getUsername());
                node.put(PASSWORD, testUser.getPassword());
                
                WSRequestHolder holder = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                holder.setHeader(BridgeConstants.BRIDGE_HOST_HEADER, "api" + BridgeConfigFactory.getConfig().getStudyHostnamePostfix());
                Response response = holder.post(node).get(TIMEOUT);
                
                WS.Cookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                // All of a sudden, there's no cookie being set. I have no idea why.
                assertNotNull("There's a cookie", cookie);

                String sessionToken = cookie.getValue();
                assertTrue("Cookie is not empty", StringUtils.isNotBlank(sessionToken));

                response = WS.url(TEST_BASE_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue()).get().get(TIMEOUT);

                cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);
                assertEquals("Cookie has been set to empty string", "", cookie.getValue());

                // TODO: Spring-ify or re-write this.
                JedisStringOps stringOps = new JedisStringOps();
                String output = stringOps.get(sessionToken);
                assertNull("Should no longer be session data", output);
            }
        });
    }
    
}
