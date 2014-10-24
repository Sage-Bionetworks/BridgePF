package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_BAD_REQUEST;
import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.commons.httpclient.HttpStatus.SC_FORBIDDEN;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_PLANS_URL;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_PLAN_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SchedulePlanControllerTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    private UserSession session;
    
    @Before
    public void before() {
        session = helper.createUser(getClass().getSimpleName(), Lists.newArrayList(helper.getTestStudy().getResearcherRole()));
    }
    
    @After
    public void after() {
        helper.deleteUser(session, getClass().getSimpleName());
    }

    @Test
    public void normalUserCannotAccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                UserSession session = null;
                try {
                    session = helper.createUser(new SignUp("normal-test-user", "normal-test-user@sagebridge.org",
                            "P4ssword"), helper.getTestStudy(), true, true);
                    
                    SchedulePlan plan = new TestABSchedulePlan();
                    String json = BridgeObjectMapper.get().writeValueAsString(plan);
                    
                    Response response = TestUtils.getURL(session.getSessionToken(), SCHEDULE_PLANS_URL).post(json).get(TIMEOUT);
                    assertEquals("Returns 403", SC_FORBIDDEN, response.getStatus());
                } finally {
                    helper.deleteUser(session);
                }
            }
        });
    }
    
    @Test
    public void crudSchedulePlan() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                SchedulePlan plan = new TestABSchedulePlan();
                String json = BridgeObjectMapper.get().writeValueAsString(plan);
                
                // Create
                Response response = TestUtils.getURL(session.getSessionToken(), SCHEDULE_PLANS_URL).post(json).get(TIMEOUT);
                assertEquals("Returns 201", SC_CREATED, response.getStatus());
                JsonNode node = response.asJson();
                assertNotNull("Returned plan has a guid", node.get("guid").asText());
                assertEquals("Type is SchedulePlan", "GuidVersionHolder", node.get("type").asText());
                
                // Update
                TestSimpleSchedulePlan simplePlan = new TestSimpleSchedulePlan();
                plan = BridgeObjectMapper.get().treeToValue(node, DynamoSchedulePlan.class);
                plan.setStrategy(simplePlan.getStrategy());
                plan.setVersion(node.get("version").asLong());
                json = BridgeObjectMapper.get().writeValueAsString(plan);
                
                String url = String.format(SCHEDULE_PLAN_URL, plan.getGuid());
                response = TestUtils.getURL(session.getSessionToken(), url).post(json).get(TIMEOUT);
                assertEquals("Returns 200", SC_OK, response.getStatus());
                
                // Get
                response = TestUtils.getURL(session.getSessionToken(), url).get().get(TIMEOUT); 
                node = response.asJson();
                assertEquals("Returns 200", SC_OK, response.getStatus());
                assertEquals("Type is SchedulePlan", "SchedulePlan", node.get("type").asText());
                assertEquals("Strategy type has been changed", "SimpleScheduleStrategy", node.get("strategy").get("type").asText());
                
                // Delete
                response = TestUtils.getURL(session.getSessionToken(), url).delete().get(TIMEOUT);
                assertEquals("Returns 200", SC_OK, response.getStatus());
            }
        });
    }
    
    @Test
    public void invalidPlanReturns400Error() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                SchedulePlan plan = new TestABSchedulePlan();
                plan.setStrategy(null); // invalid
                String json = BridgeObjectMapper.get().writeValueAsString(plan);
                
                // Create
                Response response = TestUtils.getURL(session.getSessionToken(), SCHEDULE_PLANS_URL).post(json).get(TIMEOUT);
                assertEquals("Returns 400", SC_BAD_REQUEST, response.getStatus());
                
                JsonNode node = response.asJson();
                ArrayNode strategyErrors = (ArrayNode)node.get("errors").get("strategy");
                assertEquals("Has a strategy field error", 1, strategyErrors.size());
            }
        });
    }
    
    @Test
    public void noPlanReturns400() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                // Create
                Response response = TestUtils.getURL(session.getSessionToken(), SCHEDULE_PLANS_URL).post("").get(TIMEOUT);
                assertEquals("Returns 400", SC_BAD_REQUEST, response.getStatus());
            }
        });
    }
    
}
