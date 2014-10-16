package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.NEW_SURVEY_RESPONSE;
import static org.sagebionetworks.bridge.TestConstants.SURVEY_RESPONSE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USER_SURVEY_URL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponseDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyResponseControllerTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private DynamoSurveyResponseDao responseDao;
    
    @Resource
    private DynamoSurveyDao surveyDao;
    
    private ObjectMapper mapper = new ObjectMapper();
    private TestSurvey survey;
    private String responseGuid;
    private UserSession session;
    
    @Before
    public void before() {
        session = helper.createUser(Lists.newArrayList(BridgeConstants.ADMIN_GROUP));
        survey = new TestSurvey(true);
        surveyDao.createSurvey(survey);
        DynamoInitializer.init(DynamoSurveyResponse.class);
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class);
    }
    
    @After
    public void after() {
        if (responseGuid != null) {
            responseDao.deleteSurveyResponse(responseDao.getSurveyResponse(responseGuid));
        }
        if (survey != null) {
            surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());    
        }
        helper.deleteUser(session);
    }
    
    @Test
    public void submitAnswersColdForASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                // 
                SurveyQuestion question1 = survey.getBooleanQuestion();
                SurveyQuestion question2 = survey.getIntegerQuestion();
                
                List<SurveyAnswer> list = Lists.newArrayList();
                SurveyAnswer answer = new SurveyAnswer();
                answer.setQuestionGuid(question1.getGuid());
                answer.setAnswer(Boolean.TRUE);
                answer.setClient("test");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                list.add(answer);
                
                answer = new SurveyAnswer();
                answer.setQuestionGuid(question2.getGuid());
                answer.setAnswer(null);
                answer.setDeclined(true);
                answer.setClient("test");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                list.add(answer);
                
                String body = mapper.writeValueAsString(list);
                
                String url = String.format(NEW_SURVEY_RESPONSE, survey.getGuid(), DateUtils.convertToISODateTime(survey.getVersionedOn()));
                Response response = TestUtils.getURL(session.getSessionToken(), url).post(body).get(TIMEOUT);

                assertEquals("Create new record returns 201", SC_CREATED, response.getStatus());
                
                responseGuid = getGuid(response.getBody());
                
                SurveyResponse surveyResponse = responseDao.getSurveyResponse(responseGuid);
                assertEquals("There should be two answers", 2, surveyResponse.getAnswers().size());
                
                // Check the types of this response object
                
                url = String.format(SURVEY_RESPONSE_URL, responseGuid);
                response = TestUtils.getURL(session.getSessionToken(), url).get().get(TIMEOUT);
                JsonNode node = response.asJson();
                assertEquals("Type is SurveyResponse", "SurveyResponse", node.get("type").asText());

                JsonNode answerNode = node.get("answers").get(0);
                assertEquals("Type is SurveyAnswer", "SurveyAnswer", answerNode.get("type").asText());
            }
        });
    }
    
    @Test
    public void canSubmitEveryKindOfAnswerType() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                ArrayNode array = JsonNodeFactory.instance.arrayNode();
                
                SurveyQuestion question = survey.getQuestions().get(0); // boolean
                SurveyAnswer answer = new SurveyAnswer();
                answer.setAnswer(true);
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(1); // datetime
                answer = new SurveyAnswer();
                answer.setAnswer(DateUtils.getCurrentISODateTime());
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(2); // datetime
                answer = new SurveyAnswer();
                answer.setAnswer(DateUtils.getCurrentISODateTime());
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(3); // decimal
                answer = new SurveyAnswer();
                answer.setAnswer(4.6f);
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(4); // integer
                answer = new SurveyAnswer();
                answer.setAnswer(4);
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(5); // duration
                answer = new SurveyAnswer();
                answer.setAnswer("PT4H");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(6); // time
                answer = new SurveyAnswer();
                // "14:45:15.357Z" doesn't work because it has a time zone. They should be able 
                // to enter a time zone if they want though, right? This is *too* restrictive.
                answer.setAnswer("14:45:15.357");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(7); // multichoice integer
                assertTrue("Question is multi-choice", question.getConstraints() instanceof MultiValueConstraints);
                answer = new SurveyAnswer();
                answer.setAnswer(Lists.newArrayList(3));
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                question = survey.getQuestions().get(8); // multichoice integer
                answer = new SurveyAnswer();
                answer.setAnswer("123-456-7890");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                answer.setClient("mobile");
                answer.setQuestionGuid(question.getGuid());
                array.add(mapper.valueToTree(answer));
                
                // Submit all these tricky examples of answers through the API.
                String url = String.format(USER_SURVEY_URL, survey.getGuid(), DateUtils.convertToISODateTime(survey.getVersionedOn()));
                Response response = TestUtils.getURL(session.getSessionToken(), url).post(array.toString()).get(TIMEOUT);
                assertEquals("Response successful", SC_CREATED, response.getStatus());
            }
        });
    }
    
    private String getGuid(String body) throws Exception {
        JsonNode object = mapper.readTree(body);
        return object.get("guid").asText(); 
    }

}
