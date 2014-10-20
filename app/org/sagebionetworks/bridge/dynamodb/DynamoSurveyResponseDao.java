package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.validators.SurveyAnswerValidator;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DynamoSurveyResponseDao implements SurveyResponseDao {

    private DynamoDBMapper responseMapper;
    
    private DynamoSurveyDao surveyDao;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSurveyResponse.class));
        responseMapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    @Override
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyVersionedOn, String healthCode, List<SurveyAnswer> answers) {
        if (StringUtils.isBlank(surveyGuid)) {
            throw new BadRequestException("Survey guid cannot be null/blank");
        } else if (surveyVersionedOn == 0L) {
            throw new BadRequestException("Survey versionedOn cannot be 0");
        } else if (answers == null) {
            throw new BadRequestException("Survey answers cannot be null");
        } else if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException("Health code cannot be null/blank");
        }
        Survey survey = surveyDao.getSurvey(surveyGuid, surveyVersionedOn);
        List<SurveyAnswer> unionOfAnswers = getUnionOfValidMostRecentAnswers(survey, Collections.<SurveyAnswer>emptyList(), answers);
        
        SurveyResponse response = new DynamoSurveyResponse();
        response.setGuid(BridgeUtils.generateGuid());
        response.setSurvey(survey);
        response.setAnswers(unionOfAnswers);
        response.setHealthCode(healthCode);
        updateTimestamps(response);
        
        try {
            responseMapper.save(response);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(response);
        }
        return response;
    }

    @Override
    public SurveyResponse getSurveyResponse(String guid) {
        DynamoDBQueryExpression<DynamoSurveyResponse> query = new DynamoDBQueryExpression<DynamoSurveyResponse>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(new DynamoSurveyResponse(guid));
        List<DynamoSurveyResponse> results = responseMapper.queryPage(DynamoSurveyResponse.class, query).getResults();
        if (results == null || results.isEmpty()) {
            throw new EntityNotFoundException(SurveyResponse.class);
        }
        DynamoSurveyResponse response = results.get(0);
        // Now add survey
        Survey survey = surveyDao.getSurvey(response.getSurveyGuid(), response.getSurveyVersionedOn());
        response.setSurvey(survey);
        return response;
    }
    
    @Override
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers) {
        List<SurveyAnswer> unionOfAnswers = getUnionOfValidMostRecentAnswers(response.getSurvey(), response.getAnswers(), answers);
        response.setAnswers(unionOfAnswers);
        updateTimestamps(response);

        try {
            responseMapper.save(response);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(response);
        }
        return response;
    }
    
    @Override
    public void deleteSurveyResponse(SurveyResponse response) {
        responseMapper.delete(response);
    }
    
    private Map<String,SurveyQuestion> getQuestionsMap(Survey survey) {
        return asMap(survey.getQuestions(), new Function<SurveyQuestion,String>() {
            public String apply(SurveyQuestion question) {
                return question.getGuid();
            }
        });
    }
    
    private Map<String,SurveyAnswer> getAnswerMap(List<SurveyAnswer> answers) {
        return asMap(answers, new Function<SurveyAnswer,String>() {
            public String apply(SurveyAnswer answer) {
                return answer.getQuestionGuid();
            }
        });
    }
    
    private <S,T> Map<S,T> asMap(List<T> list, Function<T,S> function) {
        Map<S,T> map = Maps.newHashMap();
        if (list != null && function != null) {
            for (T item : list) {
                map.put(function.apply(item), item);
            }
        }
        return map;
    }
    
    private List<SurveyAnswer> getUnionOfValidMostRecentAnswers(Survey survey, List<SurveyAnswer> existingAnswers,
            List<SurveyAnswer> answers) {
        // If any answer is not valid, then throw an exception and abort. We need to validate
        // these individually
        Map<String,SurveyQuestion> questions = getQuestionsMap(survey);
        for (int i=0; i < answers.size(); i++) {
            SurveyAnswer answer = answers.get(i);
            SurveyAnswerValidator validator = new SurveyAnswerValidator(questions.get(answer.getQuestionGuid()));
            validator.validate(answer);
        }
        
        // Now verify these answers are unique or more recent than existing answers, and only include them if they are.
        Map<String,SurveyAnswer> answersMap = getAnswerMap(existingAnswers);
        for (Iterator<SurveyAnswer> i = answers.iterator(); i.hasNext();) {
            SurveyAnswer newAnswer = i.next();
            SurveyAnswer existingAnswer = answersMap.get(newAnswer.getQuestionGuid());
            
            if (existingAnswer == null || newAnswer.getAnsweredOn() > existingAnswer.getAnsweredOn()) {
                answersMap.put(newAnswer.getQuestionGuid(), newAnswer);
            }
        }
        return Lists.newArrayList(answersMap.values()); 
    }
    
    private void updateTimestamps(SurveyResponse response) {
        List<SurveyAnswer> answers = response.getAnswers();
        if (answers != null) {
            long earliestDate = Long.MAX_VALUE;
            long latestDate = Long.MIN_VALUE;
            for (SurveyAnswer answer : answers) {
                if (answer.getAnsweredOn() < earliestDate) {
                    earliestDate = answer.getAnsweredOn();
                }
                if (answer.getAnsweredOn() > latestDate) {
                    latestDate = answer.getAnsweredOn();
                }
            }
            if (!answers.isEmpty()) {
                response.setStartedOn(earliestDate);    
            }
            if (response.getAnswers().size() == response.getSurvey().getQuestions().size()) {
                response.setCompletedOn(latestDate);
            }
        }
    }
}
