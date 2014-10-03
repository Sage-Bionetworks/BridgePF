package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PublishedSurveyException;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.validators.SurveyValidator;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DynamoSurveyDao implements SurveyDao {
    
    private static final SurveyValidator VALIDATOR = new SurveyValidator();
    
    Comparator<DynamoSurvey> VERSIONED_ON_DESC_SORTER = new Comparator<DynamoSurvey>() {
        @Override public int compare(DynamoSurvey o1, DynamoSurvey o2) {
            return (int)(o2.getVersionedOn() - o1.getVersionedOn());
        }
    };
    
    class QueryBuilder {
        
        private static final String PUBLISHED_PROPERTY = "published";
        private static final String VERSIONED_ON_PROPERTY = "versionedOn";
        private static final String STUDY_KEY_PROPERTY = "studyKey";
        
        String surveyGuid;
        String studyKey;
        long versionedOn;
        boolean published;
        
        QueryBuilder setSurvey(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }
        QueryBuilder setStudy(String studyKey) {
            this.studyKey = studyKey;
            return this;
        }
        QueryBuilder setVersionedOn(long versionedOn) {
            this.versionedOn = versionedOn;
            return this;
        }
        QueryBuilder isPublished() {
            this.published = true;
            return this;
        }
        
        List<Survey> getAll(boolean exceptionIfEmpty) {
            List<DynamoSurvey> dynamoSurveys = null;
            if (surveyGuid == null) {
                dynamoSurveys = scan();
            } else {
                dynamoSurveys = query();
            }
            if (exceptionIfEmpty && dynamoSurveys.size() == 0) {
                throw new EntityNotFoundException(DynamoSurvey.class);
            }
            List<Survey> surveys = Lists.newArrayListWithCapacity(dynamoSurveys.size());
            for (DynamoSurvey s : dynamoSurveys) {
                surveys.add((Survey)s);
            }
            return surveys;
        }
        
        Survey getOne(boolean exceptionIfEmpty) {
            List<Survey> surveys = getAll(exceptionIfEmpty);
            if (!surveys.isEmpty()) {
                attachQuestions(surveys.get(0));
                return surveys.get(0);
            }
            return null;
        }

        private List<DynamoSurvey> query() {
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withScanIndexForward(false);
            query.withHashKeyValues(new DynamoSurvey(surveyGuid, versionedOn));    
            if (studyKey != null) {
                query.withQueryFilterEntry(STUDY_KEY_PROPERTY, studyCondition());
            }
            if (versionedOn != 0L) {
                query.withRangeKeyCondition(VERSIONED_ON_PROPERTY, versionedOnCondition());
            }
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, publishedCondition());
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
        }

        private List<DynamoSurvey> scan() {
            DynamoDBScanExpression scan = new DynamoDBScanExpression();
            if (studyKey != null) {
                scan.addFilterCondition(STUDY_KEY_PROPERTY, studyCondition());
            }
            if (versionedOn != 0L) {
                scan.addFilterCondition(VERSIONED_ON_PROPERTY, versionedOnCondition());
            }
            if (published) {
                scan.addFilterCondition(PUBLISHED_PROPERTY, publishedCondition());
            }
            // Scans will not sort as queries do. Sort Manually.
            List<DynamoSurvey> surveys = Lists.newArrayList(surveyMapper.scan(DynamoSurvey.class, scan));
            Collections.sort(surveys, VERSIONED_ON_DESC_SORTER);
            return surveys;
        }

        private Condition publishedCondition() {
            Condition condition = new Condition();
            condition.withComparisonOperator(ComparisonOperator.EQ);
            condition.withAttributeValueList(new AttributeValue().withN("1"));
            return condition;
        }

        private Condition studyCondition() {
            Condition studyCond = new Condition();
            studyCond.withComparisonOperator(ComparisonOperator.EQ);
            studyCond.withAttributeValueList(new AttributeValue().withS(studyKey));
            return studyCond;
        }

        private Condition versionedOnCondition() {
            Condition rangeCond = new Condition();
            rangeCond.withComparisonOperator(ComparisonOperator.EQ);
            rangeCond.withAttributeValueList(new AttributeValue().withN(Long.toString(versionedOn)));
            return rangeCond;
        }
        
        private void attachQuestions(Survey survey) {
            DynamoSurveyQuestion template = new DynamoSurveyQuestion();
            template.setSurveyKeyComponents(survey.getGuid(), survey.getVersionedOn());
            
            DynamoDBQueryExpression<DynamoSurveyQuestion> query = new DynamoDBQueryExpression<DynamoSurveyQuestion>();
            query.withHashKeyValues(template);
            
            QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class,
                    query);
            
            List<SurveyQuestion> questions = Lists.newArrayList();
            for (DynamoSurveyQuestion question : page.getResults()) {
                questions.add((SurveyQuestion)question);
            }
            survey.setQuestions(questions);
        }
    }

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
        VALIDATOR.validateNew(survey);
        survey.setGuid(BridgeUtils.generateGuid());
        
        long time = DateUtils.getCurrentMillisFromEpoch();
        survey.setVersionedOn(time);
        survey.setModifiedOn(time);
        
        return saveSurvey(survey);
    }

    @Override
    public Survey publishSurvey(String surveyGuid, long versionedOn) {
        Survey survey = getSurvey(surveyGuid, versionedOn);
        if (!survey.isPublished()) {
            survey.setPublished(true);
            survey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
            try {
                surveyMapper.save(survey);
            } catch(ConditionalCheckFailedException e) {
                throw new ConcurrentModificationException(survey);
            }
        }
        return survey;
    }
    
    @Override
    public Survey updateSurvey(Survey survey) {
        Survey existing = getSurvey(survey.getGuid(), survey.getVersionedOn());
        if (existing.isPublished()) {
            throw new PublishedSurveyException(survey);
        }
        existing.setIdentifier(survey.getIdentifier());
        existing.setName(survey.getName());
        existing.setQuestions(survey.getQuestions());
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        return saveSurvey(survey);
    }
    
    @Override
    public Survey versionSurvey(String surveyGuid, long versionedOn) {
        DynamoSurvey existing = (DynamoSurvey)getSurvey(surveyGuid, versionedOn);
        DynamoSurvey copy = new DynamoSurvey(existing);
        copy.setPublished(false);
        copy.setVersion(null);
        long time = DateUtils.getCurrentMillisFromEpoch();
        copy.setVersionedOn(time);
        copy.setModifiedOn(time);

        for (SurveyQuestion question : copy.getQuestions()) {
            question.setGuid(null);
        }
        return saveSurvey(copy);
    }

    @Override
    public List<Survey> getSurveys(String studyKey) {
        if (StringUtils.isBlank(studyKey)) {
            throw new BadRequestException("Study key is required");
        }
        return new QueryBuilder().setStudy(studyKey).getAll(false);
    }
    
    @Override
    public List<Survey> getSurveyVersions(String surveyGuid) {
        if (StringUtils.isBlank(surveyGuid)) {
            throw new BadRequestException("Survey GUID is required");
        }
        return new QueryBuilder().setSurvey(surveyGuid).getAll(true);
    }

    @Override
    public Survey getSurvey(String surveyGuid, long versionedOn) {
        if (StringUtils.isBlank(surveyGuid)) {
            throw new BadRequestException("Survey GUID cannot be null/blank");
        } else if (versionedOn == 0L) {
            throw new BadRequestException("Survey must have versionedOn date");
        }
        return new QueryBuilder().setSurvey(surveyGuid).setVersionedOn(versionedOn).getOne(true);
    }

    @Override
    public List<Survey> getMostRecentlyPublishedSurveys(String studyKey) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyKey).isPublished().getAll(false);
        if (surveys.isEmpty()) {
            return surveys;
        }
        // Find the most recent. I believe they will all be at the front of the list, FWIW.
        Map<String, Survey> map = Maps.newLinkedHashMap();
        for (Survey survey : surveys) {
            Survey stored = map.get(survey.getGuid());
            if (stored == null || survey.getVersionedOn() > stored.getVersionedOn()) {
                map.put(survey.getGuid(), survey);
            }
        }
        return new ArrayList<Survey>(map.values());
    }    

    @Override
    public List<Survey> getMostRecentSurveys(String studyKey) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyKey).getAll(false);
        if (surveys.isEmpty()) {
            return surveys;
        }
        // Find the most recent. I believe they will all be at the front of the list, FWIW.
        Map<String, Survey> map = Maps.newLinkedHashMap();
        for (Survey survey : surveys) {
            Survey stored = map.get(survey.getGuid());
            if (stored == null || survey.getVersionedOn() > stored.getVersionedOn()) {
                map.put(survey.getGuid(), survey);
            }
        }
        return new ArrayList<Survey>(map.values());
    }

    @Override
    public void deleteSurvey(String surveyGuid, long versionedOn) {
        Survey existing = getSurvey(surveyGuid, versionedOn);
        if (existing.isPublished()) {
            throw new PublishedSurveyException(existing);
        }
        deleteAllQuestions(existing.getGuid(), existing.getVersionedOn());
        surveyMapper.delete(existing);
    }
    
    @Override
    public Survey closeSurvey(String surveyGuid, long versionedOn) {
        // TODO: Eventually this must do much more than this, it must 
        // also close out any existing survey response records.
        Survey existing = getSurvey(surveyGuid, versionedOn);
        existing.setPublished(false);
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        try {
            surveyMapper.save(existing);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(existing);
        }
        return existing;
    }
    
    private Survey saveSurvey(Survey survey) {
        deleteAllQuestions(survey.getGuid(), survey.getVersionedOn());
        List<SurveyQuestion> questions = survey.getQuestions();
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setSurveyKeyComponents(survey.getGuid(), survey.getVersionedOn());
            question.setOrder(i);
            if (question.getGuid() == null) {
                question.setGuid(BridgeUtils.generateGuid());
            }
        }
        // Now it should be valid, by jingo
        VALIDATOR.validate(survey);
        
        Throwable error = null;
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            try {
                surveyQuestionMapper.save(question);    
            } catch(Throwable throwable) {
                error = throwable;
            }
        }
        try {
            surveyMapper.save(survey);    
        } catch(Throwable throwable) {
            error = throwable;
        }
        // this will only be the last exception if there was more than one
        if (error != null) { 
            if (error.getClass() == ConditionalCheckFailedException.class) {
                throw new ConcurrentModificationException(survey);
            } else if (error != null) {
                throw new BridgeServiceException(error);
            }
        }
        return survey;
    }
    
    private void deleteAllQuestions(String surveyGuid, long versionedOn) {
        DynamoSurveyQuestion template = new DynamoSurveyQuestion();
        template.setSurveyKeyComponents(surveyGuid, versionedOn);
        
        DynamoDBQueryExpression<DynamoSurveyQuestion> query = new DynamoDBQueryExpression<DynamoSurveyQuestion>();
        query.withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class, query);
        List<FailedBatch> failures = surveyQuestionMapper.batchDelete(page.getResults());
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
