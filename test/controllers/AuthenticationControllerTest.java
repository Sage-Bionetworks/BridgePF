package controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULES_API;
import static org.sagebionetworks.bridge.TestConstants.SIGN_IN_URL;
import static org.sagebionetworks.bridge.TestConstants.SIGN_OUT_URL;
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
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.schedules.Activity;
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

                response = WS.url(TEST_BASE_URL + SIGN_OUT_URL)
                        .setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue()).get().get(TIMEOUT);

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
                    saveSecondStudyWithSchedulePlan();
                    
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put(STUDY_PROPERTY, testUser.getStudy().getIdentifier());
                    node.put(USERNAME, testUser.getUsername());
                    node.put(PASSWORD, testUser.getPassword());
                    
                    WSRequest request = WS.url(TEST_BASE_URL + SIGN_IN_URL);
                    WSResponse response = request.post(node).get(TIMEOUT);
                    WSCookie cookie = response.getCookie(BridgeConstants.SESSION_TOKEN_HEADER);

                    // Now, try and access schedules in the wrong study (one with a plan), you do not get it.
                    request = WS.url(TEST_BASE_URL + SCHEDULES_API);
                    request.setHeader(BridgeConstants.SESSION_TOKEN_HEADER, cookie.getValue());
                    response = request.get().get(TIMEOUT);
                    assertEquals("{\"items\":[],\"total\":0,\"type\":\"ResourceList\"}", response.getBody());
                    
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
    
    private void saveSecondStudyWithSchedulePlan() {
        String id = RandomStringUtils.randomAlphabetic(7).toLowerCase();
        secondStudy = new DynamoStudy();
        secondStudy.setIdentifier(id);
        secondStudy.setName("Second Test Study");
        studyService.createStudy(secondStudy);
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setLabel("Schedule label");
        schedule.setDelay("P1D");
        schedule.addTimes("08:00");
        schedule.getActivities().add(new Activity("An Activity", "task:AAA"));
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        // Create a schedule plan for a task that we can look for in this study...
        plan = new DynamoSchedulePlan();
        plan.setLabel("Required schedule plan label");
        plan.setStudyKey(id);
        plan.setStrategy(strategy);
        
        plan = schedulePlanService.createSchedulePlan(plan);
    }
    
}
