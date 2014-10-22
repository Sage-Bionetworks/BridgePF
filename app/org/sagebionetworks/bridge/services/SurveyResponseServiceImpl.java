package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.validators.SurveyAnswerValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.base.Function;

public class SurveyResponseServiceImpl implements SurveyResponseService {

    private SurveyResponseDao surveyResponseDao;
    private DynamoSurveyDao surveyDao;

    public void setSurveyResponseDao(SurveyResponseDao surveyResponseDao) {
        this.surveyResponseDao = surveyResponseDao;
    }

    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }

    @Override
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyVersionedOn, String healthDataCode,
            List<SurveyAnswer> answers) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey guid cannot be null/blank");
        checkArgument(surveyVersionedOn != 0L, "Survey versionedOn cannot be 0");
        checkNotNull(answers, "Survey answers cannot be null");
        checkArgument(StringUtils.isNotBlank(healthDataCode), "Health code cannot be null/blank");
        
        Survey survey = surveyDao.getSurvey(surveyGuid, surveyVersionedOn);
        validate(answers, survey);
        return surveyResponseDao.createSurveyResponse(surveyGuid, surveyVersionedOn, healthDataCode, answers);
    }

    @Override
    public SurveyResponse getSurveyResponse(String guid) {
        checkNotNull(guid, "Survey response guid cannot be null");
        
        return surveyResponseDao.getSurveyResponse(guid);
    }

    @Override
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers) {
        checkNotNull(response, "Survey response cannot be null");
        checkNotNull(answers, "Survey answers cannot be null");
        
        validate(answers, response.getSurvey());
        return surveyResponseDao.appendSurveyAnswers(response, answers);
    }

    @Override
    public void deleteSurveyResponse(SurveyResponse response) {
        checkNotNull(response, "Survey response cannot be null");
        
        surveyResponseDao.deleteSurveyResponse(response);
    }

    private void validate(List<SurveyAnswer> answers, Survey survey) {
        Map<String, SurveyQuestion> questions = getQuestionsMap(survey);
        for (int i = 0; i < answers.size(); i++) {
            SurveyAnswer answer = answers.get(i);
            SurveyAnswerValidator validator = new SurveyAnswerValidator(questions.get(answer
                    .getQuestionGuid()));
            Validate.entityThrowingException(validator, answer);
        }
    }

    private Map<String, SurveyQuestion> getQuestionsMap(Survey survey) {
        return BridgeUtils.asMap(survey.getQuestions(), new Function<SurveyQuestion, String>() {
            public String apply(SurveyQuestion question) {
                return question.getGuid();
            }
        });
    }
}
