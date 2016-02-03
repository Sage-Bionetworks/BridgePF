package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULED_ACTIVITIES_API;
import static org.sagebionetworks.bridge.TestConstants.SIGN_IN_URL;
import static org.sagebionetworks.bridge.TestConstants.SIGN_OUT_URL;
import static org.sagebionetworks.bridge.TestConstants.STUDIES_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.StudyService;
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
public class AuthenticationControllerTest {

    @Resource
    private JedisOps jedisOps;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private SchedulePlanService schedulePlanService;
    
    private TestUser testUser;
    
    private Study secondStudy;
    
    @Before
    public void before() {
        testUser = helper.getBuilder(AuthenticationControllerTest.class).build();
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }

    @Test
    public void canSignOut() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(STUDY_PROPERTY, testUser.getStudyIdentifier().getIdentifier());
                node.put(EMAIL, testUser.getEmail());
                node.put(PASSWORD, testUser.getPassword());
                
                WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                WSResponse response = request.post(node).get(TIMEOUT);

                WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                // All of a sudden, there's no cookie being set. I have no idea why.
                assertNotNull("There's a cookie", cookie);
                
                String sessionToken = cookie.getValue();
                assertTrue("Cookie is not empty", StringUtils.isNotBlank(sessionToken));

                ObjectNode emptyNode = JsonNodeFactory.instance.objectNode();
                response = WS.url(TEST_BASE_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue()).post(emptyNode).get(TIMEOUT);

                cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);
                assertEquals("Cookie has been set to empty string", "", cookie.getValue());

                String output = jedisOps.get(sessionToken);
                assertNull("Should no longer be session data", output);
            }
        });
    }
    
    @Test
    public void onceAuthenticatedUserCannotSwitchStudies() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    saveSecondStudy();
                    
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put(STUDY_PROPERTY, testUser.getStudy().getIdentifier());
                    node.put(EMAIL, testUser.getEmail());
                    node.put(PASSWORD, testUser.getPassword());
                    
                    WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                    WSResponse response = request.post(node).get(TIMEOUT);
                    WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                    // Now, try and access the wrong study, you do not get it.
                    // There's actually no easy way to request another study at this point...
                    request = WS.url(TEST_BASE_URL + STUDIES_URL + secondStudy.getIdentifier());
                    request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue());
                    response = request.get().get(TIMEOUT);
                    assertEquals("{\"message\":\"Caller does not have permission to access this service.\"}", response.getBody());
                } finally {
                    if (secondStudy != null) {
                        studyService.deleteStudy(secondStudy.getIdentifier());
                    }
                }
            }
        });
    }
    
    @Test
    public void adminUserGetsExceptionAccessingParticipantAPI() {
        TestUser dev = helper.getBuilder(AuthenticationControllerTest.class).withConsent(false).withSignIn(false)
                .withStudy(testUser.getStudy()).withRoles(Roles.DEVELOPER).build();
        
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put(STUDY_PROPERTY, dev.getStudy().getIdentifier());
                    node.put(EMAIL, dev.getEmail());
                    node.put(PASSWORD, dev.getPassword());
                    
                    WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                    WSResponse response = request.post(node).get(TIMEOUT);
                    WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                    // Now, try and access scheduled activities ,this should throw exception.
                    request = WS.url(TEST_BASE_URL + SCHEDULED_ACTIVITIES_API);
                    request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue());
                    response = request.get().get(TIMEOUT);
                    
                    String bodyString = response.getBody();
                    assertEquals(412, response.getStatus());
                    assertTrue(bodyString.contains("\"authenticated\":true"));
                    assertTrue(bodyString.contains("\"consented\":false"));
                } finally {
                    if (dev != null) {
                        helper.deleteUser(dev);    
                    }
                }
            }
        });
    }
    
    private void saveSecondStudy() {
        String id = TestUtils.randomName(AuthenticationControllerTest.class);
        secondStudy = TestUtils.getValidStudy(AuthenticationControllerTest.class);
        secondStudy.setIdentifier(id);
        studyService.createStudy(secondStudy);
    }
    
}
