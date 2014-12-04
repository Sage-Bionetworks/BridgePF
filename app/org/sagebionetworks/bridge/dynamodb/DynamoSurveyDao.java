package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dao.SurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
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
    
    Comparator<DynamoSurvey> VERSIONED_ON_DESC_SORTER = new Comparator<DynamoSurvey>() {
        @Override public int compare(DynamoSurvey o1, DynamoSurvey o2) {
            return (int)(o2.getCreatedOn() - o1.getCreatedOn());
        }
    };
    
    class QueryBuilder {
        
        private static final String PUBLISHED_PROPERTY = "published";
        private static final String CREATED_ON_PROPERTY = "versionedOn";
        private static final String STUDY_KEY_PROPERTY = "studyKey";
        
        String surveyGuid;
        String studyKey;
        long createdOn;
        boolean published;
        
        QueryBuilder setSurvey(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }
        QueryBuilder setStudy(String studyKey) {
            this.studyKey = studyKey;
            return this;
        }
        QueryBuilder setCreatedOn(long createdOn) {
            this.createdOn = createdOn;
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
            query.withHashKeyValues(new DynamoSurvey(surveyGuid, createdOn));    
            if (studyKey != null) {
                query.withQueryFilterEntry(STUDY_KEY_PROPERTY, studyCondition());
            }
            if (createdOn != 0L) {
                query.withRangeKeyCondition(CREATED_ON_PROPERTY, createdOnCondition());
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
            if (createdOn != 0L) {
                scan.addFilterCondition(CREATED_ON_PROPERTY, createdOnCondition());
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

        private Condition createdOnCondition() {
            Condition rangeCond = new Condition();
            rangeCond.withComparisonOperator(ComparisonOperator.EQ);
            rangeCond.withAttributeValueList(new AttributeValue().withN(Long.toString(createdOn)));
            return rangeCond;
        }
        
        private void attachQuestions(Survey survey) {
            DynamoSurveyQuestion template = new DynamoSurveyQuestion();
            template.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            
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
    private SurveyResponseDao responseDao;
    private SchedulePlanDao schedulePlanDao;
    
    public void setSurveyResponseDao(SurveyResponseDao responseDao) {
        this.responseDao = responseDao;
    }
    
    public void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurvey.class)).build();
        surveyMapper = new DynamoDBMapper(client, mapperConfig);
        
        mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurveyQuestion.class)).build();
        surveyQuestionMapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey.getStudyKey(), "Survey study key is null");
        if (survey.getGuid() == null) {
            survey.setGuid(BridgeUtils.generateGuid());
        }
        long time = DateUtils.getCurrentMillisFromEpoch();
        survey.setCreatedOn(time);
        survey.setModifiedOn(time);
        return saveSurvey(survey);
    }

    @Override
    public Survey publishSurvey(String surveyGuid, long createdOn) {
        Survey survey = getSurvey(surveyGuid, createdOn);
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
        Survey existing = getSurvey(survey.getGuid(), survey.getCreatedOn());
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
    public Survey versionSurvey(String surveyGuid, long createdOn) {
        DynamoSurvey existing = (DynamoSurvey)getSurvey(surveyGuid, createdOn);
        DynamoSurvey copy = new DynamoSurvey(existing);
        copy.setPublished(false);
        copy.setVersion(null);
        long time = DateUtils.getCurrentMillisFromEpoch();
        copy.setCreatedOn(time);
        copy.setModifiedOn(time);
        for (SurveyQuestion question : copy.getQuestions()) {
            question.setGuid(BridgeUtils.generateGuid());
        }
        return saveSurvey(copy);
    }

    @Override
    public List<Survey> getSurveys(String studyKey) {
        return new QueryBuilder().setStudy(studyKey).getAll(false);
    }
    
    @Override
    public List<Survey> getSurveyVersions(String surveyGuid) {
        return new QueryBuilder().setSurvey(surveyGuid).getAll(true);
    }

    @Override
    public Survey getSurvey(String surveyGuid, long createdOn) {
        return new QueryBuilder().setSurvey(surveyGuid).setCreatedOn(createdOn).getOne(true);
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
            if (stored == null || survey.getCreatedOn() > stored.getCreatedOn()) {
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
            if (stored == null || survey.getCreatedOn() > stored.getCreatedOn()) {
                map.put(survey.getGuid(), survey);
            }
        }
        return new ArrayList<Survey>(map.values());
    }

    @Override
    public void deleteSurvey(Study study, String surveyGuid, long createdOn) {
        Survey existing = getSurvey(surveyGuid, createdOn);
        if (existing.isPublished()) {
            throw new PublishedSurveyException(existing);
        }
        // If there are responses to this survey, it can't be deleted.
        List<SurveyResponse> responses = responseDao.getResponsesForSurvey(surveyGuid, createdOn);
        if (!responses.isEmpty()) {
            throw new IllegalStateException("Survey has been answered by participants; it cannot be deleted.");
        }
        // If there are schedule plans for this survey, it can't be deleted. Would need to delete them all first. 
        if (study != null) {
            List<SchedulePlan> plans = schedulePlanDao.getSchedulePlansForSurvey(study, surveyGuid, createdOn);
            if (!plans.isEmpty()) {
                throw new IllegalStateException("Survey has been scheduled; it cannot be deleted.");
            }
        }
        deleteAllQuestions(existing.getGuid(), existing.getCreatedOn());
        surveyMapper.delete(existing);
    }
    
    @Override
    public Survey closeSurvey(String surveyGuid, long createdOn) {
        Survey existing = getSurvey(surveyGuid, createdOn);
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
        deleteAllQuestions(survey.getGuid(), survey.getCreatedOn());
        List<SurveyQuestion> questions = survey.getQuestions();
        for (int i=0; i < questions.size(); i++) {
            // These shouldn't be invalid at this point, but we double-check.
            SurveyQuestion question = questions.get(i);
            question.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            question.setOrder(i);
            if (question.getGuid() == null) {
                question.setGuid(BridgeUtils.generateGuid());
            }
        }
        
        List<FailedBatch> failures = surveyQuestionMapper.batchSave(questions);
        BridgeUtils.ifFailuresThrowException(failures);
        
        try {
            surveyMapper.save(survey);    
        } catch(ConditionalCheckFailedException throwable) {
            throw new ConcurrentModificationException(survey);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
        return survey;
    }
    
    private void deleteAllQuestions(String surveyGuid, long createdOn) {
        DynamoSurveyQuestion template = new DynamoSurveyQuestion();
        template.setSurveyKeyComponents(surveyGuid, createdOn);
        
        DynamoDBQueryExpression<DynamoSurveyQuestion> query = new DynamoDBQueryExpression<DynamoSurveyQuestion>();
        query.withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class, query);
        List<FailedBatch> failures = surveyQuestionMapper.batchDelete(page.getResults());
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
