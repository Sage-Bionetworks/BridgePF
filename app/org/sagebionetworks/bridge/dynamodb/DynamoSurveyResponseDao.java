package org.sagebionetworks.bridge.dynamodb;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@Component
public class DynamoSurveyResponseDao implements SurveyResponseDao {

    private static final List<SurveyAnswer> EMPTY_ANSWERS = ImmutableList.of();
    
    private DynamoDBMapper mapper;
    private DynamoSurveyDao surveyDao;

    @Resource(name = "surveyResponseDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Autowired
    public void setSurveyDao(DynamoSurveyDao surveyDao) {
        this.surveyDao = surveyDao;
    }
    
    @Override
    public SurveyResponse createSurveyResponse(GuidCreatedOnVersionHolder keys, String healthCode,
            List<SurveyAnswer> answers, String identifier) {
        
        try {
            return createSurveyResponseInternal(keys, healthCode, answers, identifier);
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
        List<SurveyAnswer> unionOfAnswers = getUnionOfValidMostRecentAnswers(response.getAnswers(), answers);
        response.setAnswers(unionOfAnswers);
        updateTimestamps(response);

        try {
            mapper.save(response);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(response);
        }
        return response;
    }
    
    @Override
    public void deleteSurveyResponses(String healthCode) {
        DynamoSurveyResponse hashKey = new DynamoSurveyResponse();
        hashKey.setHealthCode(healthCode);
        
        DynamoDBQueryExpression<DynamoSurveyResponse> query = new DynamoDBQueryExpression<DynamoSurveyResponse>();
        query.setHashKeyValues(hashKey);
        PaginatedQueryList<DynamoSurveyResponse> results = mapper.query(DynamoSurveyResponse.class, query);
        
        List<DynamoSurveyResponse> responsesToDelete = Lists.newArrayList();
        responsesToDelete.addAll(results);
        
        if (!responsesToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(responsesToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public boolean surveyHasResponses(GuidCreatedOnVersionHolder keys) {
        DynamoSurveyResponse hashKey = new DynamoSurveyResponse();
        hashKey.setSurveyKey(keys);
        
        DynamoDBQueryExpression<DynamoSurveyResponse> query = new DynamoDBQueryExpression<DynamoSurveyResponse>();
        // Error w/o this; "Consistent reads are not supported on global secondary indexes"
        query.setConsistentRead(false); 
        query.setHashKeyValues(hashKey);
        
        return mapper.count(DynamoSurveyResponse.class, query) > 0;
    }
    
    private SurveyResponse createSurveyResponseInternal(GuidCreatedOnVersionHolder keys, String healthCode,
            List<SurveyAnswer> answers, String identifier) {

        List<SurveyAnswer> unionOfAnswers = getUnionOfValidMostRecentAnswers(EMPTY_ANSWERS, answers);

        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setIdentifier(identifier);
        response.setAnswers(unionOfAnswers);
        response.setHealthCode(healthCode);
        response.setSurveyKey(keys);
        updateTimestamps(response);
        
        try {
            mapper.save(response);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(response);
        }
        return response;
    }
    
    private DynamoSurveyResponse getSurveyResponseInternal(String healthCode, String identifier) {
        DynamoSurveyResponse hashKey = new DynamoSurveyResponse();
        hashKey.setHealthCode(healthCode);
        
        DynamoDBQueryExpression<DynamoSurveyResponse> query = new DynamoDBQueryExpression<DynamoSurveyResponse>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(hashKey);
        query.withRangeKeyCondition("identifier", new Condition()
            .withComparisonOperator(ComparisonOperator.EQ)
            .withAttributeValueList(new AttributeValue().withS(identifier)));
        List<DynamoSurveyResponse> results = mapper.queryPage(DynamoSurveyResponse.class, query).getResults();
        if (results == null || results.isEmpty()) {
            return null;
        }
        // Now add survey
        DynamoSurveyResponse response = results.get(0);
        return response;
    }

    private Map<String,SurveyAnswer> getAnswerMap(List<SurveyAnswer> answers) {
        return BridgeUtils.asMap(answers, new Function<SurveyAnswer,String>() {
            public String apply(SurveyAnswer answer) {
                return answer.getQuestionGuid();
            }
        });
    }
    
    private List<SurveyAnswer> getUnionOfValidMostRecentAnswers(List<SurveyAnswer> existingAnswers,
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
            GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(response);
            Survey survey = surveyDao.getSurvey(keys);
            if (!answers.isEmpty()) {
                response.setStartedOn(earliestDate);    
            }
            if (response.getAnswers().size() == survey.getUnmodifiableQuestionList().size()) {
                response.setCompletedOn(latestDate);
            }
        }
    }
}
