package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.PublishedSurveyException;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dao.SurveyNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

public class DynamoSurveyDao implements SurveyDao {

    private DynamoDBMapper surveyMapper;
    private DynamoDBMapper surveyQuestionMapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSurvey.class));
        surveyMapper = new DynamoDBMapper(client, mapperConfig);
        
        mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSurveyQuestion.class));
        surveyQuestionMapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Override
    public Survey createSurvey(Survey survey) {
        if (!isNew(survey)) {
            throw new BridgeServiceException("Cannot create an already created survey", 400);
        } else if (!isValid(survey)) {
            throw new BridgeServiceException("Survey is invalid (most likely missing required fields)", 400);
        }
        survey.setGuid(generateId());
        survey.setVersionedOn(DateTime.now(DateTimeZone.UTC).getMillis());
        
        return saveSurvey(survey);
        /*
        List<SurveyQuestion> questions = nomod(survey.getQuestions());
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setSurveyGuid(survey.getGuid());
            question.setGuid(generateId());
            question.setOrder(i);
            surveyQuestionMapper.save(question);
        }
        surveyMapper.save(survey);
        return survey;
        */
    }

    @Override
    public Survey publishSurvey(Survey surveyKey) {
        verifyKeyFields(surveyKey);
        Survey survey = getSurvey(surveyKey.getStudyKey(), surveyKey.getGuid(), surveyKey.getVersionedOn());
        if (survey.isPublished()) {
            return survey;
        }
        try {
            Survey existingPublished = getPublishedSurvey(survey.getStudyKey(), survey.getGuid());
            existingPublished.setPublished(false);
            surveyMapper.save(existingPublished);
        } catch(SurveyNotFoundException snfe) {
            // This is fine
        }
        survey.setPublished(true);
        surveyMapper.save(survey);
        return survey;
    }
    
    @Override
    public Survey updateSurvey(Survey survey) {
        if (isNew(survey)) {
            throw new BridgeServiceException("Cannot update a new survey before you create it", 400);
        } else if (!isValid(survey)) {
            throw new BridgeServiceException("Survey is invalid (most likely missing required fields)", 400);
        }
        Survey existingSurvey = getSurvey(survey.getStudyKey(), survey.getGuid(), survey.getVersionedOn());
        if (existingSurvey.isPublished()) {
            throw new PublishedSurveyException(survey);
        }
        // Cannot change publication state from false
        survey.setPublished(false);
        
        return saveSurvey(survey);
    }
    
    @Override
    public Survey versionSurvey(Survey surveyKey) {
        verifyKeyFields(surveyKey);
        Survey existing = getSurvey(surveyKey.getStudyKey(), surveyKey.getGuid(), surveyKey.getVersionedOn());
        Survey copy = new DynamoSurvey(existing);
        
        copy.setVersionedOn(DateTime.now(DateTimeZone.UTC).getMillis());

        for (SurveyQuestion question : copy.getQuestions()) {
            question.setGuid(null);
        }
        return saveSurvey(copy);
    }

    @Override
    public List<Survey> getSurveys(String studyKey) {
        if (StringUtils.isBlank(studyKey)) {
            throw new BridgeServiceException("Study key is required", 400);
        }
        DynamoSurvey survey = new DynamoSurvey();
        survey.setStudyKey(studyKey);

        return findSurveys(survey);
    }
    
    @Override
    public List<Survey> getSurveys(String studyKey, String surveyGuid) {
        if (StringUtils.isBlank(studyKey)) {
            throw new BridgeServiceException("Study key is required", 400);
        } else if (StringUtils.isBlank(surveyGuid)) {
            throw new BridgeServiceException("Survey GUID is required", 400);
        }
        DynamoSurvey survey = new DynamoSurvey();
        survey.setStudyKey(studyKey);
        survey.setGuid(surveyGuid);

        return findSurveys(survey);
    }

    private List<Survey> findSurveys(DynamoSurvey hashKey) {
        Condition rangeKeyCondition = null;
        if (hashKey.getGuid() != null) {
            rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(hashKey.getGuid()));
        }
        DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
        query.withHashKeyValues(hashKey);
        query.withScanIndexForward(false);
        if (rangeKeyCondition != null) {
            query.withRangeKeyCondition("guid", rangeKeyCondition);
        }
        QueryResultPage<DynamoSurvey> page = surveyMapper.queryPage(DynamoSurvey.class, query);
        if (page == null || page.getResults().size() == 0) {
            throw new SurveyNotFoundException(hashKey.getStudyKey(), null, 0L);
        }
        List<Survey> list = Lists.newArrayListWithCapacity(page.getResults().size());
        for (Survey survey : page.getResults()) {
            list.add((Survey)survey);
        }
        return list;
    }

    @Override
    public Survey getSurvey(String studyKey, String surveyGuid, long versionedOn) {
        DynamoSurvey hashKey = new DynamoSurvey();
        hashKey.setStudyKey(studyKey);
        hashKey.setGuid(surveyGuid);

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withN(Long.toString(versionedOn)));
        
        DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
        query.withHashKeyValues(hashKey);
        query.withScanIndexForward(false);
        query.withQueryFilterEntry("versionedOn", condition);
        QueryResultPage<DynamoSurvey> page = surveyMapper.queryPage(DynamoSurvey.class, query);
        if (page == null || page.getResults().size() == 0) {
            throw new SurveyNotFoundException(studyKey, surveyGuid, versionedOn);
        }
        Survey survey = page.getResults().get(0);
        addAllQuestions(survey);
        return survey;
    }
    
    @Override
    public Survey getPublishedSurvey(String studyKey, String surveyGuid) {
        DynamoSurvey hashKey = new DynamoSurvey();
        hashKey.setStudyKey(studyKey);
        hashKey.setGuid(surveyGuid);

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withN("1"));
        
        DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
        query.withHashKeyValues(hashKey);
        query.withScanIndexForward(false);
        query.withQueryFilterEntry("published", condition);
        
        QueryResultPage<DynamoSurvey> page = surveyMapper.queryPage(DynamoSurvey.class, query);
        if (page == null || page.getResults().size() == 0) {
            throw new SurveyNotFoundException(studyKey, surveyGuid, 0L);
        } else if (page.getResults().size() > 1) {
            throw new BridgeServiceException("Invalid state, survey has multiple published versions ("+surveyGuid+")", 500);
        }
        Survey survey = page.getResults().get(0);
        addAllQuestions(survey);
        return survey;
    }

    @Override
    public void deleteSurvey(Survey surveyKey) {
        verifyKeyFields(surveyKey);
        Survey existing = getSurvey(surveyKey.getStudyKey(), surveyKey.getGuid(), surveyKey.getVersionedOn());
        if (existing.isPublished()) {
            // something has to happen here, this could be bad. Might need to publish another survey?
        }
        surveyMapper.batchDelete(existing.getQuestions());
        surveyMapper.delete(existing);
    }
    
    @Override
    public void closeSurvey(Survey surveyKey) {
        verifyKeyFields(surveyKey);
        Survey existing = getSurvey(surveyKey.getStudyKey(), surveyKey.getGuid(), surveyKey.getVersionedOn());
        if (!existing.isPublished()) {
            throw new PublishedSurveyException(existing); 
        }
        existing.setPublished(false);
        surveyMapper.save(existing);
        // TODO:
        // Eventually this must do much more than this, it must also close out any existing survey response
        // records.
    }
    
    private String generateId() {
        return UUID.randomUUID().toString();
    }
    
    private Survey saveSurvey(Survey survey) {
        deleteAllQuestions(survey.getGuid());
        List<SurveyQuestion> questions = survey.getQuestions();
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setSurveyGuid(survey.getGuid());
            question.setOrder(i);
            // A new question was added to the survey.
            if (question.getGuid() == null) {
                question.setGuid(generateId());
            }
            surveyQuestionMapper.save(question);
        }
        surveyMapper.save(survey);
        return survey;
    }
    
    private void deleteAllQuestions(String surveyGuid) {
        DynamoSurveyQuestion template = new DynamoSurveyQuestion();
        template.setSurveyGuid(surveyGuid);
        
        DynamoDBQueryExpression<DynamoSurveyQuestion> query = new DynamoDBQueryExpression<DynamoSurveyQuestion>();
        query.withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class, query);

        for (DynamoSurveyQuestion question :  page.getResults()) {
            surveyQuestionMapper.delete(question);
        }
    }
    
    private void addAllQuestions(Survey survey) {
        DynamoSurveyQuestion template = new DynamoSurveyQuestion();
        template.setSurveyGuid(survey.getGuid());
        
        DynamoDBQueryExpression<DynamoSurveyQuestion> queryExpression = new DynamoDBQueryExpression<DynamoSurveyQuestion>()
                .withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class,
                queryExpression);
        
        List<SurveyQuestion> questions = Lists.newArrayList();
        for (DynamoSurveyQuestion question : page.getResults()) {
            questions.add((SurveyQuestion)question);
        }
        survey.setQuestions(questions);
    }
    
    private void verifyKeyFields(Survey survey) {
        if (StringUtils.isBlank(survey.getStudyKey())) {
            throw new BridgeServiceException("Survey study key cannot be null/blank", 400);
        } else if (StringUtils.isBlank(survey.getGuid())) {
            throw new BridgeServiceException("Survey GUID cannot be null/blank", 400);
        } else if (survey.getVersionedOn() == 0L) {
            throw new BridgeServiceException("Survey must have versionedOn date", 400);
        }
    }
    
    private boolean isNew(Survey survey) {
        if (survey.getGuid() != null || survey.isPublished() || survey.getVersion() != null || survey.getVersionedOn() != 0L) {
            return false;
        }
        for (SurveyQuestion question : survey.getQuestions()) {
            if (question.getGuid() != null) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isValid(Survey survey) {
        if (StringUtils.isBlank(survey.getIdentifier()) || StringUtils.isBlank(survey.getStudyKey())) {
            return false;
        }
        for (SurveyQuestion question : survey.getQuestions()) {
            if (StringUtils.isBlank(question.getIdentifier())) {
                return false;
            }
        }
        return true;
    }

}
