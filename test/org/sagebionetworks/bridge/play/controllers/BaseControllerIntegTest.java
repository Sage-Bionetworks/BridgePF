package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import play.libs.ws.WS;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import javax.annotation.Resource;

import static org.apache.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_WARNING_STATUS;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class BaseControllerIntegTest {

    @Resource
    private TestUserAdminHelper helper;

    private TestUserAdminHelper.TestUser testUser;

    @Before
    public void before() {
        testUser = helper.getBuilder(BaseControllerIntegTest.class).build();
    }

    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test
    public void setWarningHeaderWhenNoUserAgentOrNoAcceptLanguage() {
        // This is expensive so test them all after starting the server, in one test
        TestUtils.runningTestServerWithSpring(() -> {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("email", testUser.getEmail());
            node.put("password", testUser.getPassword());
            node.put("study", "api");

            // First, verify this header isn't on *every* endpoint
            WSRequest request = WS.url(TEST_BASE_URL + "/v3/auth/signIn");
            // set a valid user-agent and valid accept-language
            request.setHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
            request.setHeader(ACCEPT_LANGUAGE, "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
            WSResponse response = request.post(node).get(TIMEOUT);
            String headerValue = response.getHeader(BRIDGE_API_STATUS_HEADER);
            assertNull(headerValue);

            // Now verify user agent
            request = WS.url(TEST_BASE_URL + "/v3/auth/signIn");
            request.setHeader(USER_AGENT, null);
            request.setHeader(ACCEPT_LANGUAGE, "de-de;q=0.4,de;q=0.2,en-ca,en;q=0.8,en-us;q=0.6");
            response = request.post(node).get(TIMEOUT);
            headerValue = response.getHeader(BRIDGE_API_STATUS_HEADER);
            assertEquals(BRIDGE_WARNING_STATUS, headerValue);

            // then verify accept language
            request = WS.url(TEST_BASE_URL + "/v3/auth/signIn");
            request.setHeader(ACCEPT_LANGUAGE, null);
            request.setHeader(USER_AGENT, "Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
            response = request.post(node).get(TIMEOUT);
            headerValue = response.getHeader(BRIDGE_API_STATUS_HEADER);
            assertEquals(BRIDGE_WARNING_STATUS, headerValue);

            // finally verify both of them
            request = WS.url(TEST_BASE_URL + "/v3/auth/signIn");
            response = request.post(node).get(TIMEOUT);
            headerValue = response.getHeader(BRIDGE_API_STATUS_HEADER);
            assertEquals(BRIDGE_WARNING_STATUS, headerValue);
        });
    }
}
