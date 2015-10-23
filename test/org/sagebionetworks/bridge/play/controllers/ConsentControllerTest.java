package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_DEPRECATED_STATUS;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.ws.WS;
import play.libs.ws.WSCookie;
import play.libs.ws.WSResponse;
import play.libs.ws.WSRequest;

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
                node.put("scope", SharingScope.NO_SHARING.name().toLowerCase());
                
                // First, verify this header isn't on *every* endpoint
                WSRequest request = WS.url(TEST_BASE_URL + "/v3/consents/signature");
                WSResponse response = request.post(node).get(TIMEOUT);
                String headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertNull(headerValue);
                
                // Now verify it's on the intended endpoints
                request = WS.url(TEST_BASE_URL + "/api/v1/consent/dataSharing/suspend");
                response = request.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals(BRIDGE_DEPRECATED_STATUS, headerValue);
                
                request = WS.url(TEST_BASE_URL + "/api/v1/consent/dataSharing/resume");
                response = request.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals(BRIDGE_DEPRECATED_STATUS, headerValue);
                
                request = WS.url(TEST_BASE_URL + "/api/v1/consent");
                response = request.post(node).get(TIMEOUT);
                headerValue = response.getHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                assertEquals(BRIDGE_DEPRECATED_STATUS, headerValue);
                
            }
        });
    }
  
    @Test
    public void invalidConsentCorrectlyReturns400() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(STUDY_PROPERTY, testUser.getStudyIdentifier().getIdentifier());
                node.put(USERNAME, testUser.getUsername());
                node.put(PASSWORD, testUser.getPassword());
                
                WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                WSResponse response = request.post(node).get(TIMEOUT);

                WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);
                String sessionToken = cookie.getValue();

                System.out.println(sessionToken);
                
                node = JsonNodeFactory.instance.objectNode();
                node.put("birthdate", "1970-01-01");

                request = WS.url(TEST_BASE_URL + "/v3/consents");
                request.setHeader("Bridge-Session", sessionToken);
                response = request.post(node).get(TIMEOUT);
                
                System.out.println(response.getBody());
                System.out.println(response.getStatus());
            }
        });        
    }
    
}
