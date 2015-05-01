package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.validators.SurveyAnswerValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.MapBindingResult;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

@Component
public class SurveyResponseServiceImpl implements SurveyResponseService {

    private SurveyResponseDao surveyResponseDao;
    private DynamoSurveyDao surveyDao;

    @Autowired
    public void setSurveyResponseDao(SurveyResponseDao surveyResponseDao) {
        this.surveyResponseDao = surveyResponseDao;
    }

    @Autowired
    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }

    @Override
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers) {
        return createSurveyResponse(survey, healthCode, answers, BridgeUtils.generateGuid());
    }

    @Override
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder survey, String healthCode,
            List<SurveyAnswer> answers, String identifier) {
        checkArgument(isNotBlank(survey.getGuid()), CANNOT_BE_BLANK, "survey guid");
        checkArgument(isNotBlank(identifier), CANNOT_BE_BLANK, "identifier");
        checkArgument(isNotBlank(healthCode), CANNOT_BE_BLANK, "health code");
        checkArgument(survey.getCreatedOn() != 0L, "Survey createdOn cannot be 0");
        checkNotNull(answers, CANNOT_BE_NULL, "survey answers");

        Survey existing = surveyDao.getSurvey(survey);
        validate(answers, existing);
        return surveyResponseDao.createSurveyResponse(existing, healthCode, answers, identifier);
    }
    
    @Override
    public SurveyResponse getSurveyResponse(String healthCode, String identifier) {
        checkNotNull(healthCode, CANNOT_BE_NULL, "health code");
        checkNotNull(identifier, CANNOT_BE_NULL, "guid");
        
        return surveyResponseDao.getSurveyResponse(healthCode, identifier);
    }

    @Override
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers) {
        checkNotNull(response, CANNOT_BE_NULL, "survey response");
        checkNotNull(answers, CANNOT_BE_NULL, "survey answers");
        
        validate(answers, response.getSurvey());
        return surveyResponseDao.appendSurveyAnswers(response, answers);
    }
    
    @Override
    public void deleteSurveyResponses(String healthCode) {
        checkNotNull(healthCode, CANNOT_BE_NULL, "healthCode");
        
        surveyResponseDao.deleteSurveyResponses(healthCode);
    }

    private void validate(List<SurveyAnswer> answers, Survey survey) {
        Map<String, SurveyQuestion> questions = getQuestionsMap(survey.getUnmodifiableQuestionList());
        
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "SurveyResponse");
        for (int i = 0; i < answers.size(); i++) {
            SurveyAnswer answer = answers.get(i);
            SurveyQuestion question = questions.get(answer.getQuestionGuid());
            SurveyAnswerValidator validator = new SurveyAnswerValidator(question);
            Validate.entity(validator, errors, answer);
        }
        Validate.throwException(errors, survey);
    }

    private Map<String, SurveyQuestion> getQuestionsMap(List<SurveyQuestion> questions) {
        return BridgeUtils.asMap(questions, new Function<SurveyQuestion, String>() {
            public String apply(SurveyQuestion element) {
                return element.getGuid();
            }
        });
    }
    
}
