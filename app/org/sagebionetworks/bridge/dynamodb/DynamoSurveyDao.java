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
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

@Component
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
        private static final String IDENTIFIER_PROPERTY = "identifier";
        
        String surveyGuid;
        String studyIdentifier;
        String identifier;
        long createdOn;
        boolean published;
        
        QueryBuilder setSurvey(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }
        QueryBuilder setStudy(StudyIdentifier studyIdentifier) {
            this.studyIdentifier = studyIdentifier.getIdentifier();
            return this;
        }
        QueryBuilder setIdentifier(String identifier) {
            this.identifier = identifier;
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
                attachSurveyElements(surveys.get(0));
                return surveys.get(0);
            }
            return null;
        }

        private List<DynamoSurvey> query() {
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withScanIndexForward(false);
            query.withHashKeyValues(new DynamoSurvey(surveyGuid, createdOn));    
            if (studyIdentifier != null) {
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
            if (studyIdentifier != null) {
                scan.addFilterCondition(STUDY_KEY_PROPERTY, studyCondition());
            }
            if (createdOn != 0L) {
                scan.addFilterCondition(CREATED_ON_PROPERTY, createdOnCondition());
            }
            if (published) {
                scan.addFilterCondition(PUBLISHED_PROPERTY, publishedCondition());
            }
            if (identifier != null) {
                scan.addFilterCondition(IDENTIFIER_PROPERTY, identifierCondition());
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
            studyCond.withAttributeValueList(new AttributeValue().withS(studyIdentifier));
            return studyCond;
        }

        private Condition identifierCondition() {
            Condition studyCond = new Condition();
            studyCond.withComparisonOperator(ComparisonOperator.EQ);
            studyCond.withAttributeValueList(new AttributeValue().withS(identifier));
            return studyCond;
        }
        
        private Condition createdOnCondition() {
            Condition rangeCond = new Condition();
            rangeCond.withComparisonOperator(ComparisonOperator.EQ);
            rangeCond.withAttributeValueList(new AttributeValue().withN(Long.toString(createdOn)));
            return rangeCond;
        }
        
        private void attachSurveyElements(Survey survey) {
            DynamoSurveyElement template = new DynamoSurveyElement();
            template.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            
            DynamoDBQueryExpression<DynamoSurveyElement> query = new DynamoDBQueryExpression<DynamoSurveyElement>();
            query.withHashKeyValues(template);
            
            QueryResultPage<DynamoSurveyElement> page = surveyElementMapper.queryPage(DynamoSurveyElement.class, query);

            List<SurveyElement> elements = Lists.newArrayList();
            for (DynamoSurveyElement element : page.getResults()) {
                elements.add(SurveyElementFactory.fromDynamoEntity(element));
            }
            survey.setElements(elements);
        }
    }

    private DynamoDBMapper surveyMapper;
    private DynamoDBMapper surveyElementMapper;
    private SurveyResponseDao responseDao;
    private SchedulePlanDao schedulePlanDao;

    @Autowired
    public void setSurveyResponseDao(SurveyResponseDao responseDao) {
        this.responseDao = responseDao;
    }
    
    @Autowired
    public void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurvey.class)).build();
        surveyMapper = new DynamoDBMapper(client, mapperConfig);
        
        mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoSurveyElement.class)).build();
        surveyElementMapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey.getStudyIdentifier(), "Survey study identifier is null");
        if (survey.getGuid() == null) {
            survey.setGuid(BridgeUtils.generateGuid());
        }
        long time = DateUtils.getCurrentMillisFromEpoch();
        survey.setCreatedOn(time);
        survey.setModifiedOn(time);
        return saveSurvey(survey);
    }

    @Override
    public Survey publishSurvey(GuidCreatedOnVersionHolder keys) {
        Survey survey = getSurvey(keys);
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
        Survey existing = getSurvey(survey);
        if (existing.isPublished()) {
            throw new PublishedSurveyException(survey);
        }
        existing.setIdentifier(survey.getIdentifier());
        existing.setName(survey.getName());
        existing.setElements(survey.getElements());
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        return saveSurvey(survey);
    }
    
    @Override
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys) {
        DynamoSurvey existing = (DynamoSurvey)getSurvey(keys);
        DynamoSurvey copy = new DynamoSurvey(existing);
        copy.setPublished(false);
        copy.setVersion(null);
        long time = DateUtils.getCurrentMillisFromEpoch();
        copy.setCreatedOn(time);
        copy.setModifiedOn(time);
        for (SurveyElement element : copy.getElements()) {
            element.setGuid(BridgeUtils.generateGuid());
        }
        return saveSurvey(copy);
    }

    @Override
    public void deleteSurvey(String healthCode, StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(keys);
        if (existing.isPublished()) {
            throw new PublishedSurveyException(existing);
        }
        // If there are responses to this survey, it can't be deleted.
        if (responseDao.surveyHasResponses(healthCode, keys)) {
            throw new IllegalStateException("Survey has been answered by participants; it cannot be deleted.");
        }
        // If there are schedule plans for this survey, it can't be deleted. Would need to delete them all first. 
        if (studyIdentifier != null) {
            List<SchedulePlan> plans = schedulePlanDao.getSchedulePlansForSurvey(studyIdentifier, keys);
            if (!plans.isEmpty()) {
                throw new IllegalStateException("Survey has been scheduled; it cannot be deleted.");
            }
        }
        deleteAllElements(existing.getGuid(), existing.getCreatedOn());
        surveyMapper.delete(existing);
    }
    
    @Override
    public Survey closeSurvey(GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(keys);
        existing.setPublished(false);
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        try {
            surveyMapper.save(existing);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(existing);
        }
        return existing;
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).getAll(true);
    }
    
    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).getOne(true);
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).isPublished().setSurvey(guid).getOne(true);
    }
    
    @Override
    public Survey getSurveyMostRecentlyPublishedVersionByIdentifier(StudyIdentifier studyIdentifier, String identifier) {
        return new QueryBuilder().setStudy(studyIdentifier).setIdentifier(identifier).isPublished().getOne(true);
    }
    
    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier) {
        return new QueryBuilder().setStudy(studyIdentifier).isPublished().getAll(false);
    }
    
    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyIdentifier).getAll(false);
        if (surveys.isEmpty()) {
            return surveys;
        }
        // If you knew the number of unique guids, you could iterate until you had found
        // that many unique GUIDs, and stop, since they're ordered from largest timestamp 
        // to smaller. This would be faster with many versions to go through.
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
    public Survey getSurvey(GuidCreatedOnVersionHolder keys) {
        return new QueryBuilder().setSurvey(keys.getGuid()).setCreatedOn(keys.getCreatedOn()).getOne(true);
    }
    
    private Survey saveSurvey(Survey survey) {
        deleteAllElements(survey.getGuid(), survey.getCreatedOn());
        
        List<DynamoSurveyElement> dynamoElements = Lists.newArrayList();
        for (int i=0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            element.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            element.setOrder(i);
            if (element.getGuid() == null) {
                element.setGuid(BridgeUtils.generateGuid());
            }
            dynamoElements.add((DynamoSurveyElement)element);
        }
        
        List<FailedBatch> failures = surveyElementMapper.batchSave(dynamoElements);
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
    
    private void deleteAllElements(String surveyGuid, long createdOn) {
        DynamoSurveyElement template = new DynamoSurveyElement();
        template.setSurveyKeyComponents(surveyGuid, createdOn);
        
        DynamoDBQueryExpression<DynamoSurveyElement> query = new DynamoDBQueryExpression<DynamoSurveyElement>();
        query.withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyElement> page = surveyElementMapper.queryPage(DynamoSurveyElement.class, query);
        List<FailedBatch> failures = surveyElementMapper.batchDelete(page.getResults());
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
