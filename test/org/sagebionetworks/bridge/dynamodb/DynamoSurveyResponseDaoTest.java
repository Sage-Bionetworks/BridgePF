package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyResponseDaoTest {

    private static final String SURVEY_RESPONSE_IDENTIFIER = "surveyResponseIdentifier";

    private static final String HEALTH_DATA_CODE = "AAA";

    @Resource
    DynamoSurveyResponseDao surveyResponseDao;

    @Resource
    DynamoSurveyDao surveyDao;

    private Survey survey;

    @BeforeClass
    public static void initialSetUp() {
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyResponse.class);
        DynamoTestUtil.clearTable(DynamoSurvey.class);
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class);
    }

    @Before
    public void before() throws Exception {
        survey = new TestSurvey(true);
        survey = surveyDao.createSurvey(survey);
    }

    @After
    public void after() {
        // These have to be deleted or the survey won't delete. In practice you can't
        // delete these without deleting a user, and that isn't going to happen in production.
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyResponse.class);
        DynamoTestUtil.clearTable(DynamoSurvey.class);
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class);
    }

    @Test
    public void sensitiveFieldsNotSerializedToJSON() throws Exception {
        long time = DateTime.parse("2014-10-08T07:02:21.123Z").getMillis();
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("test", "value");
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setIdentifier("foo");
        response.setHealthCode("AAA");
        response.setSurveyKey("BBB:"+time);
        response.setStartedOn(DateTime.parse("2014-10-10T10:02:21.123Z").getMillis());
        response.setVersion(1L);
        response.setData(data);
        
        String string = BridgeObjectMapper.get().writeValueAsString(response);
        JsonNode node = BridgeObjectMapper.get().readTree(string);
        
        assertEquals("foo", node.get("identifier").asText());
        assertEquals("2014-10-10T10:02:21.123Z", node.get("startedOn").asText());
        assertEquals("in_progress", node.get("status").asText());
        assertEquals("SurveyResponse", node.get("type").asText());
        assertEquals("BBB", node.get("surveyGuid").asText());
        assertEquals("2014-10-08T07:02:21.123Z", node.get("surveyCreatedOn").asText());
        assertFalse("No version in JSON", node.has("version"));
        assertFalse("No data in JSON", node.has("data"));
        assertFalse("No healthCode in JSON", node.has("healthCode"));
    }
    
    @Test
    public void createSurveyResponseWithIdentifier() {
        String identifier = RandomStringUtils.randomAlphanumeric(10);
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers, identifier);
        assertEquals("Has been assigned the supplied identifier", identifier, response.getIdentifier());

        // Do it again, it should fail.
        try {
            surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers, identifier);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            
        }
    }
    
    @Test
    public void createSurveyResponse() {
        assertTrue("Survey is not in use", noResponses(survey));
        
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers, BridgeUtils.generateGuid());
        assertNotNull("Has been assigned a GUID", response.getIdentifier());
        assertFalse("Survey is now in use", noResponses(survey));
        
        SurveyResponse newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());

        assertEquals("Has right identifier", response.getIdentifier(), newResponse.getIdentifier());
        // These are zero until some answers are submitted
        assertNull("startedOn is null", newResponse.getStartedOn());
        assertNull("completedOn is null", newResponse.getCompletedOn());

        // Now push some answers through the answer API
        SurveyAnswer answer = new SurveyAnswer();
        answer.addAnswer("false");
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getElements().get(0).getGuid());
        answers.add(answer);

        answer = new SurveyAnswer();
        answer.addAnswer(DateUtils.getCurrentCalendarDateStringInLocalTime());
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getElements().get(1).getGuid());
        answers.add(answer);

        surveyResponseDao.appendSurveyAnswers(newResponse, answers);

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("Now the response has two answers", 2, newResponse.getAnswers().size());
        assertNotNull("startedOn isn't null", newResponse.getStartedOn());
        assertNull("completedOn is null", newResponse.getCompletedOn());

        assertFalse("Survey is still in use", noResponses(survey));
        // You can't append answers to questions that have already been answered.
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("The response continues to have two answers", 2, newResponse.getAnswers().size());

        // But if you update a timestamp, it looks new and it will be updated.
        answers.get(0).setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answers.get(0).getAnswers().set(0, "true"); // eek
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("Answer was updated due to more recent timestamp", "true", answers.get(0).getAnswers().get(0));
    }
    
    @Test
    public void canTellWhenSurveyHasResponses() {
        assertFalse(surveyResponseDao.surveyHasResponses(survey));
        
        surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, Lists.<SurveyAnswer>newArrayList(), SURVEY_RESPONSE_IDENTIFIER);
        assertTrue(surveyResponseDao.surveyHasResponses(survey));
    }
    
    @Test
    public void canDeleteSurveyResponseByHealthCode() {
        surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, Lists.<SurveyAnswer>newArrayList(), SURVEY_RESPONSE_IDENTIFIER);
        
        SurveyResponse response = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, SURVEY_RESPONSE_IDENTIFIER);
        assertNotNull(response);
        
        surveyResponseDao.deleteSurveyResponses(HEALTH_DATA_CODE);
        try {
            surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, SURVEY_RESPONSE_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
    }
    
    // This test is due to a bug where the range key was not being set correctly for a query, and 
    // a list of results were being returned and the wrong response then being selected.
    // NOTE: Cannot get tests to pass right now because of a Stormpath issue.
    @Test
    public void createTwoResponsesAndRetrieveTheCorrectOneByIdentifier() {
        String targetIdentifier = BridgeUtils.generateGuid();
        surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, Lists.<SurveyAnswer>newArrayList(), BridgeUtils.generateGuid());
        surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, Lists.<SurveyAnswer>newArrayList(), targetIdentifier);
        surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, Lists.<SurveyAnswer>newArrayList(), BridgeUtils.generateGuid());
        
        SurveyResponse response = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, targetIdentifier);
        assertEquals(targetIdentifier, response.getIdentifier());
    }
    
    private boolean noResponses(Survey survey) {
        return !surveyResponseDao.surveyHasResponses(survey);
    }

}
