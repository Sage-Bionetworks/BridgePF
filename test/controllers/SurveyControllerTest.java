package controllers;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.GET_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.GET_VERSIONS_OF_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.PUBLISH_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.RECENT_PUBLISHED_SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.RECENT_SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USER_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.VERSION_SURVEY_URL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyControllerTest {

    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private DynamoSurveyDao surveyDao;
    
    private BridgeObjectMapper mapper = new BridgeObjectMapper();

    private UserSession userSession;
    private UserSession researcherSession;
    private UserSession adminSession;

    @Before
    public void before() {
        List<Survey> surveys = surveyDao.getSurveys(helper.getTestStudy().getKey());
        for (Survey survey : surveys) {
            surveyDao.closeSurvey(survey.getGuid(), survey.getVersionedOn());
            surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());
        }
        userSession = helper.createUser(new SignUp("user1", "user1@sagebridge.org", "P4ssword"), helper.getTestStudy(), true, true);
        researcherSession = helper.createUser(new SignUp("user2", "user2@sagebridge.org", "P4ssword", "teststudy_researcher"), helper.getTestStudy(), true, true);
        adminSession = helper.createUser(new SignUp("user3", "user3@sagebridge.org", "P4ssword", "admin"), helper.getTestStudy(), true, true);
    }

    @After
    public void after() throws Exception {
        helper.deleteUser(userSession);
        helper.deleteUser(researcherSession);
        helper.deleteUser(adminSession);
    }
    
    @Test
    public void mustSubmitAsAdminOrResearcher() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String content = new TestSurvey(true).toJSON(); // createSurveyObject("Name");
                
                Response response = TestUtils.getURL(userSession.getSessionToken(), SURVEYS_URL).post(content)
                        .get(TIMEOUT);
                
                assertEquals("HTTP response indicates authorization error", SC_FORBIDDEN, response.getStatus());
            }
        });
    }
    
    @Test
    public void saveAndRetrieveASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                
                GuidVersionHolder keys = createSurvey(researcherSession.getSessionToken(), "Name");
                
                JsonNode node = getSurvey(researcherSession.getSessionToken(), keys);

                ArrayNode questions = (ArrayNode)node.get("questions");
                
                String prompt = questions.get(1).get("prompt").asText();
                assertEquals("Prompt is correct", "When did you last have a medical check-up?", prompt);
            }
        });
    }
    
    @Test
    public void createVersionPublish() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {

                GuidVersionHolder keys = createSurvey(researcherSession.getSessionToken(), "Name");

                GuidVersionHolder laterKeys = versionSurvey(researcherSession.getSessionToken(), keys);
                
                JsonNode node = getSurvey(researcherSession.getSessionToken(), laterKeys);
                
                boolean isPublished = node.get("published").asBoolean();
                assertNotEquals("versionedOn has been updated", keys.versionedOn, laterKeys.versionedOn);
                assertFalse("New survey is not published", isPublished);

                isPublished = publishSurvey(researcherSession.getSessionToken(), laterKeys);
                assertTrue("New survey is published", isPublished);
            }
        });
    }
    
    @Test
    public void getAllVersionsOfASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                GuidVersionHolder keys = createSurvey(researcherSession.getSessionToken(), "Name");

                keys = versionSurvey(researcherSession.getSessionToken(), keys);

                int count = getAllVersionsOfSurveysCount(researcherSession.getSessionToken(), keys);
                assertEquals("There are two versions for this survey", 2, count);
            }
        });        
    }
    
    @Test
    public void canGetMostRecentOrRecentlyPublishedSurveys() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                GuidVersionHolder keys = createSurvey(researcherSession.getSessionToken(), "Name 1");
                keys = versionSurvey(researcherSession.getSessionToken(), keys);
                keys = versionSurvey(researcherSession.getSessionToken(), keys);

                GuidVersionHolder keys2 = createSurvey(researcherSession.getSessionToken(), "Name 2");
                keys2 = versionSurvey(researcherSession.getSessionToken(), keys2);
                keys2 = versionSurvey(researcherSession.getSessionToken(), keys2);
                
                GuidVersionHolder keys3 = createSurvey(researcherSession.getSessionToken(), "Name 3");
                keys3 = versionSurvey(researcherSession.getSessionToken(), keys3);
                keys3 = versionSurvey(researcherSession.getSessionToken(), keys3);

                List<GuidVersionHolder> versions = getMostRecentSurveys(researcherSession.getSessionToken());
                assertEquals("There are two items", 3, versions.size());
                assertEquals("Last version in list", keys3.versionedOn, versions.get(0).versionedOn);
                assertEquals("Last version in list", keys2.versionedOn, versions.get(1).versionedOn);
                
                publishSurvey(researcherSession.getSessionToken(), keys);
                publishSurvey(researcherSession.getSessionToken(), keys3);
                versions = getMostRecentlyPublishedSurveys(researcherSession.getSessionToken());
                assertEquals("One published item", 2, versions.size());
                assertEquals("Published version", keys3.versionedOn, versions.get(0).versionedOn);
                assertEquals("Published version", keys.versionedOn, versions.get(1).versionedOn);
            }
        });        
    }
    
    @Test
    public void canUpdateASurveyAndTypesAreCorrect() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                GuidVersionHolder keys = createSurvey(researcherSession.getSessionToken(), "Name");
                ObjectNode node = (ObjectNode)getSurvey(researcherSession.getSessionToken(), keys);
                node.put("name", "Name Changed");
                
                // Check all the types while we have a complete survey
                assertEquals("Type is Survey", "Survey", node.get("type").asText());
                JsonNode questions = node.get("questions");
                assertEquals("Type is SurveyQuestion", "SurveyQuestion", questions.get(0).get("type").asText());
                assertEquals("Type is BooleanConstraints", "BooleanConstraints", constraintTypeForQuestion(questions, 0));
                assertEquals("Type is DateConstraints", "DateConstraints", constraintTypeForQuestion(questions, 1));
                assertEquals("Type is DateTimeConstraints", "DateTimeConstraints", constraintTypeForQuestion(questions, 2));
                assertEquals("Type is DecimalConstraints", "DecimalConstraints", constraintTypeForQuestion(questions, 3));
                assertEquals("Type is IntegerConstraints", "IntegerConstraints", constraintTypeForQuestion(questions, 4));
                assertEquals("Type is IntegerConstraints", "SurveyRule", questions.get(4).get("constraints").get("rules").get(0).get("type").asText());
                assertEquals("Type is DurationConstraints", "DurationConstraints", constraintTypeForQuestion(questions, 5));
                assertEquals("Type is TimeConstraints", "TimeConstraints", constraintTypeForQuestion(questions, 6));
                assertEquals("Type is MultiValueConstraints", "MultiValueConstraints", constraintTypeForQuestion(questions, 7));
                assertEquals("Type is SurveyQuestionOption", "SurveyQuestionOption", questions.get(7).get("constraints").get("enumeration").get(0).get("type").asText());
                
                updateSurvey(researcherSession.getSessionToken(), keys, node);
                
                node = (ObjectNode)getSurvey(researcherSession.getSessionToken(), keys);
                String finalName = node.get("name").asText();
                assertEquals("Name has been updated", "Name Changed", finalName);
            }
        });          
    }
    
    // This would be hard to do, hopefully, since the information is not published, but we prevent it.
    @Test
    public void participantCannotRetrieveUnpublishedSurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                GuidVersionHolder keys = createSurvey(adminSession.getSessionToken(), "Name");
                
                // Get survey using the user's api, the survey is not published
                String url = String.format(USER_SURVEY_URL, keys.guid, keys.versionedOn);
                Response response = TestUtils.getURL(userSession.getSessionToken(), url).get().get(TIMEOUT);
                assertEquals("Survey not found because it is not published", 404, response.getStatus());
            }
        });          
    }
    
    public class GuidVersionHolder {
        public final String guid;
        public final String versionedOn;
        public final JsonNode node;
        public GuidVersionHolder(String guid, String versionedOn, JsonNode node) {
            this.guid = guid;
            this.versionedOn = versionedOn;
            this.node = node;
        }
        public String toString() {
            return "GuidVersionHolder [guid=" + guid + ", versionedOn=" + versionedOn + "]";
        }
    }
    
    private String constraintTypeForQuestion(JsonNode questions, int index) {
        return questions.get(index).get("constraints").get("type").asText();
    }
    
    private void updateSurvey(String sessionKey, GuidVersionHolder keys, JsonNode survey) throws Exception {
        String content = survey.toString();
        String url = String.format(GET_SURVEY_URL, keys.guid, keys.versionedOn);
        Response response = TestUtils.getURL(sessionKey, url).post(content).get(TIMEOUT);
        assertEquals("200 response [createSurvey]", SC_OK, response.getStatus());
    }
    
    private GuidVersionHolder createSurvey(String sessionKey, String name) throws Exception {
        String content = new TestSurvey(true).toJSON(); // createSurveyObject(name);
        Response response = TestUtils.getURL(sessionKey, SURVEYS_URL).post(content).get(TIMEOUT);
        assertEquals("201 response [createSurvey]", SC_CREATED, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        String surveyGuid = node.get("guid").asText();
        String timestamp = node.get("versionedOn").asText();
        return new GuidVersionHolder(surveyGuid, timestamp, node);
    }
    
    private GuidVersionHolder versionSurvey(String sessionKey, GuidVersionHolder keys) throws Exception {
        String url = String.format(VERSION_SURVEY_URL, keys.guid, keys.versionedOn); 
        Response response = TestUtils.getURL(sessionKey, url).post("").get(TIMEOUT);
        assertEquals("201 response [versionSurvey]", SC_CREATED, response.getStatus());

        JsonNode node = mapper.readTree(response.getBody());
        String surveyGuid = node.get("guid").asText();
        String versionedOn = node.get("versionedOn").asText();
        return new GuidVersionHolder(surveyGuid, versionedOn, node);
    }
    
    private boolean publishSurvey(String sessionKey, GuidVersionHolder keys) throws Exception {
        String url = String.format(PUBLISH_SURVEY_URL, keys.guid, keys.versionedOn);
        Response response = TestUtils.getURL(sessionKey, url).post("").get(TIMEOUT);
        assertEquals("200 response [publishSurvey]", SC_OK, response.getStatus());
        
        // Get it. It should be published
        url = String.format(GET_SURVEY_URL, keys.guid, keys.versionedOn);
        response = TestUtils.getURL(sessionKey, url).get().get(TIMEOUT);
        JsonNode node = mapper.readTree(response.getBody());
        
        return node.get("published").asBoolean();
    }
    
    private int getAllVersionsOfSurveysCount(String sessionKey, GuidVersionHolder keys) throws Exception {
        String url = String.format(GET_VERSIONS_OF_SURVEY_URL, keys.guid);
        Response response = TestUtils.getURL(sessionKey, url).get().get(TIMEOUT);
        assertEquals("200 response [allVersionsOfSurveysCount]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        return node.get("total").asInt();                    
    }
    
    private List<GuidVersionHolder> getMostRecentSurveys(String sessionKey) throws Exception {
        Response response = TestUtils.getURL(sessionKey, RECENT_SURVEYS_URL).get().get(TIMEOUT);
        assertEquals("200 response [mostRecentSurveys]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        List<GuidVersionHolder> list = Lists.newArrayList();
        ArrayNode array = (ArrayNode)node.get("items");
        for (int i=0; i < array.size(); i++) {
            String surveyGuid = array.get(i).get("guid").asText();
            String versionedOn = array.get(i).get("versionedOn").asText();
            list.add(new GuidVersionHolder(surveyGuid, versionedOn, array.get(i)));
        }
        return list;
    }
    
    private JsonNode getSurvey(String sessionKey, GuidVersionHolder keys) throws Exception {
        String url = String.format(GET_SURVEY_URL, keys.guid, keys.versionedOn);
        Response response = TestUtils.getURL(sessionKey, url).get().get(TIMEOUT);
        
        return mapper.readTree(response.getBody());
    }

    private List<GuidVersionHolder> getMostRecentlyPublishedSurveys(String sessionKey) throws Exception {
        Response response = TestUtils.getURL(sessionKey, RECENT_PUBLISHED_SURVEYS_URL).get().get(TIMEOUT);
        assertEquals("200 response [mostRecentSurveys]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        List<GuidVersionHolder> list = Lists.newArrayList();
        ArrayNode array = (ArrayNode)node.get("items");
        for (int i=0; i < array.size(); i++) {
            String surveyGuid = array.get(i).get("guid").asText();
            String versionedOn = array.get(i).get("versionedOn").asText();
            list.add(new GuidVersionHolder(surveyGuid, versionedOn, array.get(i)));
        }
        return list;
    }
}