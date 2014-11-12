package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class DynamoSurveyResponseDao implements SurveyResponseDao {

    private DynamoDBMapper responseMapper;
    
    private DynamoSurveyDao surveyDao;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurveyResponse.class)).build();
        responseMapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    @Override
    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyCreatedOn, String healthCode, List<SurveyAnswer> answers) {
        Survey survey = surveyDao.getSurvey(surveyGuid, surveyCreatedOn);
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
        Survey survey = surveyDao.getSurvey(response.getSurveyGuid(), response.getSurveyCreatedOn());
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
    
    private Map<String,SurveyAnswer> getAnswerMap(List<SurveyAnswer> answers) {
        return BridgeUtils.asMap(answers, new Function<SurveyAnswer,String>() {
            public String apply(SurveyAnswer answer) {
                return answer.getQuestionGuid();
            }
        });
    }
    
    private List<SurveyAnswer> getUnionOfValidMostRecentAnswers(Survey survey, List<SurveyAnswer> existingAnswers,
            List<SurveyAnswer> answers) {
        // Verify these answers are unique or more recent than existing answers, and only include them if they are.
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
