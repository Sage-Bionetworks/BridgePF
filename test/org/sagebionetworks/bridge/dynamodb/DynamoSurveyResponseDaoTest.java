package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyResponseDaoTest {

    private static final String HEALTH_DATA_CODE = "AAA";

    @Resource
    DynamoSurveyResponseDao surveyResponseDao;

    @Resource
    DynamoSurveyDao surveyDao;

    private Survey survey;

    @BeforeClass
    public static void initialSetUp() {
        DynamoInitializer.init(DynamoSurveyResponse.class);
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class);
    }

    @Before
    public void before() throws Exception {
        survey = new TestSurvey(true);
        survey = surveyDao.createSurvey(survey);
    }

    @After
    public void after() {
        surveyDao.deleteSurvey(null, survey);
        survey = null;
    }

    @Test
    public void sensitiveFieldsNotSerializedToJSON() throws Exception {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("test", "value");
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setGuid("foo");
        response.setHealthCode("AAA");
        response.setIdentifier("bar");
        response.setSurveyGuid("BBB");
        response.setSurveyCreatedOn(new Date().getTime());
        response.setVersion(1L);
        response.setData(data);
        
        String string = JsonUtils.toJSON(response);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);

        assertFalse("No guid in JSON", node.has("guid"));
        assertFalse("No version in JSON", node.has("version"));
        assertFalse("No data in JSON", node.has("data"));
        assertFalse("No healthCode in JSON", node.has("healthCode"));
        assertFalse("No surveyGuid in JSON", node.has("surveyGuid"));
        assertFalse("No surveyCreatedOn in JSON", node.has("surveyCreatedOn"));
    }
    
    @Test
    public void createSurveyResponseWithIdentifier() {
        String identifier = RandomStringUtils.randomAlphanumeric(10);
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers, identifier);
        assertEquals("Has been assigned the supplied identifier", identifier, response.getIdentifier());
        assertNotNull("Has a GUID", response.getGuid());
        assertTrue("GUID contains identifier", response.getGuid().contains(identifier));

        // Do it again, it should fail.
        try {
            surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers, identifier);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            
        }
        surveyResponseDao.deleteSurveyResponse(response);
        try {
            surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, identifier);
            fail("Should have thrown an EntityNotFoundException");
        } catch (EntityNotFoundException nfe) {

        }
    }
    
    @Test
    public void createSurveyResponse() {
        assertTrue("Survey is not in use", noResponses(survey));
        
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers);
        assertTrue("Has been assigned a GUID", response.getGuid() != null);
        assertFalse("Survey is now in use", noResponses(survey));
        
        SurveyResponse newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());

        assertEquals("Has right guid", response.getGuid(), newResponse.getGuid());
        // These are zero until some answers are submitted
        assertTrue("startedOn is 0", newResponse.getStartedOn() == 0L);
        assertTrue("completedOn is 0", newResponse.getCompletedOn() == 0L);

        // Now push some answers through the answer API
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnswer("false");
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getQuestions().get(0).getGuid());
        answers.add(answer);

        answer = new SurveyAnswer();
        answer.setAnswer(DateUtils.getCurrentISODate());
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getQuestions().get(1).getGuid());
        answers.add(answer);

        surveyResponseDao.appendSurveyAnswers(newResponse, answers);

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("Now the response has two answers", 2, newResponse.getAnswers().size());
        assertTrue("startedOn isn't 0", newResponse.getStartedOn() > 0L);
        assertTrue("completedOn is 0", newResponse.getCompletedOn() == 0L);

        assertFalse("Survey is still in use", noResponses(survey));
        // You can't append answers to questions that have already been answered.
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("The response continues to have two answers", 2, newResponse.getAnswers().size());

        // But if you update a timestamp, it looks new and it will be updated.
        answers.get(0).setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answers.get(0).setAnswer("true");
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
        assertEquals("Answer was updated due to more recent timestamp", "true", answers.get(0).getAnswer());

        // delete it
        surveyResponseDao.deleteSurveyResponse(newResponse);
        try {
            assertTrue("After deleting of response survey is again not in use", noResponses(survey));
            surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getIdentifier());
            fail("Should have thrown an EntityNotFoundException");
        } catch (EntityNotFoundException nfe) {

        }
    }
    
    private boolean noResponses(Survey survey) {
        return surveyResponseDao.getResponsesForSurvey(survey).isEmpty();
    }

}
