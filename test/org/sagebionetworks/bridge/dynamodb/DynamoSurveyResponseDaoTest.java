package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
        surveyDao.deleteSurvey(survey.getGuid(), survey.getCreatedOn());
        survey = null;
    }

    @Test
    public void createSurveyResponseWithExternalGuid() {
        String externalGuid = RandomStringUtils.randomAlphanumeric(10);
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponseWithGuid(survey.getGuid(), survey.getCreatedOn(),
                HEALTH_DATA_CODE, answers, externalGuid);
        assertEquals("Has been assigned the supplied GUID", externalGuid, response.getGuid());

        // Do it again, it should fail.
        try {
            surveyResponseDao.createSurveyResponseWithGuid(survey.getGuid(), survey.getCreatedOn(),
                    HEALTH_DATA_CODE, answers, externalGuid);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            
        }
        surveyResponseDao.deleteSurveyResponse(response);
        try {
            surveyResponseDao.getSurveyResponse(externalGuid);
            fail("Should have thrown an EntityNotFoundException");
        } catch (EntityNotFoundException nfe) {

        }
    }
    
    @Test
    public void createSurveyResponse() {
        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey.getGuid(), survey.getCreatedOn(),
                HEALTH_DATA_CODE, answers);
        assertTrue("Has been assigned a GUID", response.getGuid() != null);

        SurveyResponse newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());

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

        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("Now the response has two answers", 2, newResponse.getAnswers().size());
        assertTrue("startedOn isn't 0", newResponse.getStartedOn() > 0L);
        assertTrue("completedOn is 0", newResponse.getCompletedOn() == 0L);

        // You can't append answers to questions that have already been answered.
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("The response continues to have two answers", 2, newResponse.getAnswers().size());

        // But if you update a timestamp, it looks new and it will be updated.
        answers.get(0).setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answers.get(0).setAnswer("true");
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());

        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("Answer was updated due to more recent timestamp", "true", answers.get(0).getAnswer());

        // delete it
        surveyResponseDao.deleteSurveyResponse(newResponse);
        try {
            surveyResponseDao.getSurveyResponse(response.getGuid());
            fail("Should have thrown an EntityNotFoundException");
        } catch (EntityNotFoundException nfe) {

        }
    }

}
