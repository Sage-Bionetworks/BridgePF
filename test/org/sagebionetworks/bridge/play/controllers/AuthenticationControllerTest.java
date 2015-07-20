package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULES_API;
import static org.sagebionetworks.bridge.TestConstants.SIGN_IN_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USERNAME;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import play.libs.ws.WSResponse;
import play.libs.ws.WSRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private StudyServiceImpl studyService;
    
    @Resource
    private SchedulePlanServiceImpl schedulePlanService;
    
    private TestUser testUser;
    
    private SchedulePlan plan;
    
    private Study secondStudy;
    
    @Before
    public void before() {
        testUser = helper.createUser(AuthenticationControllerTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }
    
    @Test
    public void canSignIn() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(STUDY_PROPERTY, testUser.getStudy().getIdentifier());
                node.put(USERNAME, testUser.getUsername());
                node.put(PASSWORD, testUser.getPassword());
                
                WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                WSResponse response = request.post(node).get(TIMEOUT);
                
                String sessionToken = new ObjectMapper().readTree(response.getBody()).get("sessionToken").asText();

                // There's actually no way to *request* stuff from another study at this point,
                // so this test hardly makes sense at this point.
                request = WS.url(TEST_BASE_URL + SCHEDULES_API);
                request.setHeader("Bridge-Session", sessionToken);
                response = request.get().get(TIMEOUT);
                assertEquals("{\"items\":[],\"total\":0,\"type\":\"ResourceList\"}", response.getBody());
            }
        });
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

                JsonNode responseNode = new ObjectMapper().readTree(response.getBody());
                String sessionToken = responseNode.get("sessionToken").asText();
                
                String output = jedisOps.get(sessionToken);
                assertNull("Should no longer be session data", output);
            }
        });
    }
    
}
