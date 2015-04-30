package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
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
        // These have to be deleted or the survey won't delete. In practice you can't
        // delete these without deleting a user, and that isn't going to happen in production.
        DynamoInitializer.init(DynamoSurveyResponse.class);
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class);
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
        response.setSurveyGuid("BBB");
        response.setSurveyCreatedOn(new Date().getTime());
        response.setStartedOn(DateTime.parse("2014-10-10T10:02:21.123Z").getMillis());
        response.setVersion(1L);
        response.setData(data);
        
        String string = new BridgeObjectMapper().writeValueAsString(response);
        assertEquals("{\"guid\":\"foo\",\"startedOn\":\"2014-10-10T10:02:21.123Z\",\"answers\":[],\"status\":\"in_progress\",\"type\":\"SurveyResponse\"}", string);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);

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
        assertEquals("Has been assigned the supplied identifier", identifier, response.getGuid());
        assertNotNull("Has a GUID", response.getGuid());
        assertTrue("GUID contains identifier", response.getGuid().contains(identifier));

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

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey, HEALTH_DATA_CODE, answers);
        assertTrue("Has been assigned a GUID", response.getGuid() != null);
        assertFalse("Survey is now in use", noResponses(survey));
        
        SurveyResponse newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getGuid());

        assertEquals("Has right guid", response.getGuid(), newResponse.getGuid());
        // These are zero until some answers are submitted
        assertTrue("startedOn is null", newResponse.getStartedOn() == null);
        assertTrue("completedOn is null", newResponse.getCompletedOn() == null);

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

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getGuid());
        assertEquals("Now the response has two answers", 2, newResponse.getAnswers().size());
        assertNotNull("startedOn isn't null", newResponse.getStartedOn());
        assertNull("completedOn is null", newResponse.getCompletedOn());

        assertFalse("Survey is still in use", noResponses(survey));
        // You can't append answers to questions that have already been answered.
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getGuid());
        assertEquals("The response continues to have two answers", 2, newResponse.getAnswers().size());

        // But if you update a timestamp, it looks new and it will be updated.
        answers.get(0).setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answers.get(0).getAnswers().set(0, "true"); // eek
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getGuid());

        newResponse = surveyResponseDao.getSurveyResponse(HEALTH_DATA_CODE, response.getGuid());
        assertEquals("Answer was updated due to more recent timestamp", "true", answers.get(0).getAnswers().get(0));
    }
    
    private boolean noResponses(Survey survey) {
        return !surveyResponseDao.surveyHasResponses(survey);
    }

}
