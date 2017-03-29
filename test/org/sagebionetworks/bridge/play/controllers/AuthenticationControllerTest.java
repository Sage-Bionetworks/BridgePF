package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
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

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.libs.ws.WSRequest;

import com.fasterxml.jackson.databind.JsonNode;
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
        TestUtils.runningTestServerWithSpring(() -> {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put(STUDY_PROPERTY, testUser.getStudyIdentifier().getIdentifier());
            node.put(EMAIL, testUser.getEmail());
            node.put(PASSWORD, testUser.getPassword());

            WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
            WSResponse response = request.post(node).get(TIMEOUT);

            JsonNode sessNode = BridgeObjectMapper.get().readTree(response.getBody());
            String sessionToken = sessNode.get("sessionToken").asText();
            
            ObjectNode emptyNode = JsonNodeFactory.instance.objectNode();
            response = WS.url(TEST_BASE_URL + SIGN_OUT_URL)
                    .setHeader(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken).post(emptyNode).get(TIMEOUT);

            String output = jedisOps.get(sessionToken);
            assertNull("Should no longer be session data", output);
        });
    }
    
    @Test
    public void onceAuthenticatedUserCannotSwitchStudies() {
        TestUtils.runningTestServerWithSpring(() -> {
            try {
                saveSecondStudy();

                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(STUDY_PROPERTY, testUser.getStudy().getIdentifier());
                node.put(EMAIL, testUser.getEmail());
                node.put(PASSWORD, testUser.getPassword());

                WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                WSResponse response = request.post(node).get(TIMEOUT);
                
                JsonNode sessNode = BridgeObjectMapper.get().readTree(response.getBody());
                String sessionToken = sessNode.get("sessionToken").asText();

                // Now, try and access the wrong study, you do not get it.
                // There's actually no easy way to request another study at this point...
                request = WS.url(TEST_BASE_URL + STUDIES_URL + secondStudy.getIdentifier());
                request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken);
                response = request.get().get(TIMEOUT);
                assertEquals("{\"statusCode\":403,\"message\":\"Caller does not have permission to access this service.\",\"type\":\"UnauthorizedException\"}",
                        response.getBody());
            } finally {
                if (secondStudy != null) {
                    studyService.deleteStudy(secondStudy.getIdentifier(), true);
                }
            }
        });
    }
    
    @Test
    public void adminUserGetsExceptionAccessingParticipantAPI() {
        TestUser dev = helper.getBuilder(AuthenticationControllerTest.class).withConsent(false).withSignIn(false)
                .withStudy(testUser.getStudy()).withRoles(Roles.DEVELOPER).build();

        TestUtils.runningTestServerWithSpring(() -> {
            try {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(STUDY_PROPERTY, dev.getStudy().getIdentifier());
                node.put(EMAIL, dev.getEmail());
                node.put(PASSWORD, dev.getPassword());

                WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                WSResponse response = request.post(node).get(TIMEOUT);
                
                JsonNode sessNode = BridgeObjectMapper.get().readTree(response.getBody());
                String sessionToken = sessNode.get("sessionToken").asText();

                // Now, try and access scheduled activities ,this should throw exception.
                request = WS.url(TEST_BASE_URL + SCHEDULED_ACTIVITIES_API);
                request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken);
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
        });
    }
    
    private void saveSecondStudy() {
        String id = TestUtils.randomName(AuthenticationControllerTest.class);
        secondStudy = TestUtils.getValidStudy(AuthenticationControllerTest.class);
        secondStudy.setIdentifier(id);
        studyService.createStudy(secondStudy);
    }
    
}
