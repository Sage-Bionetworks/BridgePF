package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

@Component
public class DynamoSurveyResponseDao implements SurveyResponseDao {

    private DynamoDBMapper responseMapper;
    private DynamoSurveyDao surveyDao;
    private Function<DynamoSurveyResponse,SurveyResponse> TRANSFORMER = new Function<DynamoSurveyResponse,SurveyResponse>() {
        @Override
        public SurveyResponse apply(DynamoSurveyResponse res) {
            return res;
        }
    };

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurveyResponse.class)).build();
        responseMapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Autowired
    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    @Override
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder keys, String healthCode,
            List<SurveyAnswer> answers) {
        return createSurveyResponseInternal(keys, healthCode, answers, BridgeUtils.generateGuid());
    }

    @Override
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder keys, String healthCode,
            List<SurveyAnswer> answers, String identifier) {
        try {
            SurveyResponse response = getSurveyResponseInternal(healthCode, identifier);
            if (response == null) {
                // This is the response we want. This is good, carry on.
                return createSurveyResponseInternal(keys, healthCode, answers, identifier);
            }
            throw new EntityAlreadyExistsException(response);
        } catch(ConcurrentModificationException e) {
            // This can happen due to the version not being correct, as we're only checking the identifier;
            throw new EntityAlreadyExistsException(e.getEntity());
        }
    }
    
    @Override
    public SurveyResponse getSurveyResponse(String healthCode, String identifier) {
        DynamoSurveyResponse response = getSurveyResponseInternal(healthCode, identifier);
        if (response == null) {
            throw new EntityNotFoundException(SurveyResponse.class);
        }
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
    
    @Override
    public List<SurveyResponse> getResponsesForSurvey(GuidCreatedOnVersionHolder keys) {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(keys.getGuid()));
        scan.addFilterCondition("surveyGuid", condition);
        
        Condition condition2 = new Condition();
        condition2.withComparisonOperator(ComparisonOperator.EQ);
        condition2.withAttributeValueList(new AttributeValue().withN(Long.toString(keys.getCreatedOn())));
        scan.addFilterCondition("surveyCreatedOn", condition2);

        List<DynamoSurveyResponse> mappings = responseMapper.scan(DynamoSurveyResponse.class, scan);
        return Lists.transform(mappings, TRANSFORMER);
    }
    
    private SurveyResponse createSurveyResponseInternal(GuidCreatedOnVersionHolder keys, String healthCode,
            List<SurveyAnswer> answers, String identifier) {
        
        Survey survey = surveyDao.getSurvey(keys);
        List<SurveyAnswer> unionOfAnswers = getUnionOfValidMostRecentAnswers(survey, Collections.<SurveyAnswer>emptyList(), answers);
        
        SurveyResponse response = new DynamoSurveyResponse();
        response.setIdentifier(identifier);
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
    
    private DynamoSurveyResponse getSurveyResponseInternal(String healthCode, String identifier) {
        DynamoDBQueryExpression<DynamoSurveyResponse> query = new DynamoDBQueryExpression<DynamoSurveyResponse>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(new DynamoSurveyResponse(healthCode, identifier));
        List<DynamoSurveyResponse> results = responseMapper.queryPage(DynamoSurveyResponse.class, query).getResults();
        if (results == null || results.isEmpty()) {
            return null;
        }
        // Now add survey
        DynamoSurveyResponse response = results.get(0);
        Survey survey = surveyDao.getSurvey(new GuidCreatedOnVersionHolderImpl(response.getSurveyGuid(), response.getSurveyCreatedOn()));
        response.setSurvey(survey);
        return response;
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
            if (response.getAnswers().size() == response.getSurvey().getUnmodifiableQuestionList().size()) {
                response.setCompletedOn(latestDate);
            }
        }
    }
}
