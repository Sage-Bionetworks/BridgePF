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
import org.springframework.validation.MapBindingResult;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

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
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyCreatedOn, String healthCode,
            List<SurveyAnswer> answers) {
        checkArgument(StringUtils.isNotBlank(surveyGuid), "Survey guid cannot be null/blank");
        checkArgument(surveyCreatedOn != 0L, "Survey createdOn cannot be 0");
        checkNotNull(answers, "Survey answers cannot be null");
        checkArgument(StringUtils.isNotBlank(healthCode), "Health code cannot be null/blank");
        
        Survey survey = surveyDao.getSurvey(surveyGuid, surveyCreatedOn);
        validate(answers, survey);
        return surveyResponseDao.createSurveyResponse(surveyGuid, surveyCreatedOn, healthCode, answers);
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
        
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "SurveyResponse");
        for (int i = 0; i < answers.size(); i++) {
            SurveyAnswer answer = answers.get(i);
            SurveyQuestion question = questions.get(answer.getQuestionGuid());
            SurveyAnswerValidator validator = new SurveyAnswerValidator(question);
            Validate.entity(validator, errors, answer);
        }
        Validate.throwException(errors, survey);
    }

    private Map<String, SurveyQuestion> getQuestionsMap(Survey survey) {
        return BridgeUtils.asMap(survey.getQuestions(), new Function<SurveyQuestion, String>() {
            public String apply(SurveyQuestion question) {
                return question.getGuid();
            }
        });
    }
}
