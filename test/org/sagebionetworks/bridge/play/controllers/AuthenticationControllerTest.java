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
import static org.sagebionetworks.bridge.TestConstants.USERNAME;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.services.SchedulePlanServiceImpl;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.ws.WS;
import play.libs.ws.WSCookie;
import play.libs.ws.WSResponse;
import play.libs.ws.WSRequest;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationControllerTest {

    @Resource
    private JedisOps jedisOps;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private StudyServiceImpl studyService;
    
    @Resource
    private SchedulePlanServiceImpl schedulePlanService;
    
    private TestUser testUser;
    
    private Study secondStudy;
    
    private SchedulePlan plan;
    
    @Before
    public void before() {
        testUser = helper.createUser(AuthenticationControllerTest.class);
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
                node.put(USERNAME, testUser.getUsername());
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
                    node.put(USERNAME, testUser.getUsername());
                    node.put(PASSWORD, testUser.getPassword());
                    
                    WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                    WSResponse response = request.post(node).get(TIMEOUT);
                    WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                    // Now, try and access schedules in the wrong study (one with a plan), you do not get it.
                    // There's actually no easy way to request another study at this point...
                    request = WS.url(TEST_BASE_URL + STUDIES_URL + secondStudy.getIdentifier());
                    request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue());
                    response = request.get().get(TIMEOUT);
                    assertEquals("{\"message\":\"Caller does not have permission to access this service.\"}", response.getBody());
                    
                } finally {
                    if (secondStudy != null) {
                        if (plan != null) {
                            schedulePlanService.deleteSchedulePlan(secondStudy.getStudyIdentifier(), plan.getGuid());        
                        }
                        studyService.deleteStudy(secondStudy.getIdentifier());
                    }
                }
            }
        });
    }
    
    @Test
    public void adminUserGetsExceptionAccessingParticipantAPI() {
        TestUser dev = helper.createUser(AuthenticationControllerTest.class, false, false, Sets.newHashSet(Roles.DEVELOPER));
        
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put(STUDY_PROPERTY, dev.getStudy().getIdentifier());
                    node.put(USERNAME, dev.getUsername());
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
        String id = RandomStringUtils.randomAlphabetic(7).toLowerCase();
        secondStudy = TestUtils.getValidStudy(AuthenticationControllerTest.class);
        secondStudy.setIdentifier(id);
        studyService.createStudy(secondStudy);
    }
    
}
