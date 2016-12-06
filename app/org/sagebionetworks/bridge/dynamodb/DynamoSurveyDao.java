package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoSurveyDao implements SurveyDao {

    class QueryBuilder {
        
        private static final String PUBLISHED_PROPERTY = "published";
        private static final String DELETED_PROPERTY = "deleted";
        private static final String CREATED_ON_PROPERTY = "versionedOn";
        private static final String STUDY_KEY_PROPERTY = "studyKey";
        
        String surveyGuid;
        String studyIdentifier;
        long createdOn;
        boolean published;
        boolean notDeleted;
        
        QueryBuilder setSurvey(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }
        QueryBuilder setStudy(StudyIdentifier studyIdentifier) {
            this.studyIdentifier = studyIdentifier.getIdentifier();
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
        QueryBuilder isNotDeleted() {
            this.notDeleted = true;
            return this;
        }
        
        int getCount() {
            DynamoSurvey key = new DynamoSurvey();
            key.setGuid(surveyGuid);
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withScanIndexForward(false);
            query.withHashKeyValues(key);    
            if (studyIdentifier != null) {
                query.withQueryFilterEntry(STUDY_KEY_PROPERTY, equalsString(studyIdentifier));
            }
            if (createdOn != 0L) {
                query.withRangeKeyCondition(CREATED_ON_PROPERTY, equalsNumber(Long.toString(createdOn)));
            }
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getCount();
        }
        
        List<Survey> getAll(boolean exceptionIfEmpty) {
            List<DynamoSurvey> dynamoSurveys = null;
            if (surveyGuid == null) {
                dynamoSurveys = queryBySecondaryIndex();
            } else {
                dynamoSurveys = query();
            }
            if (exceptionIfEmpty && dynamoSurveys.size() == 0) {
                throw new EntityNotFoundException(DynamoSurvey.class);
            }
            return ImmutableList.copyOf(dynamoSurveys);
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
                query.withQueryFilterEntry(STUDY_KEY_PROPERTY, equalsString(studyIdentifier));
            }
            if (createdOn != 0L) {
                query.withRangeKeyCondition(CREATED_ON_PROPERTY, equalsNumber(Long.toString(createdOn)));
            }
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
        }
        
        private List<DynamoSurvey> queryBySecondaryIndex() {
            if (studyIdentifier == null) {
                throw new IllegalStateException("Calculated the need to query by secondary index, but study identifier is not set");
            }
            DynamoSurvey hashKey = new DynamoSurvey();
            hashKey.setStudyIdentifier(studyIdentifier);
            
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withHashKeyValues(hashKey);
            // DDB will throw an error if you don't set this to eventual consistency.
            query.withConsistentRead(false);
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
        }

        private Condition equalsNumber(String equalTo) {
            Condition condition = new Condition();
            condition.withComparisonOperator(ComparisonOperator.EQ);
            condition.withAttributeValueList(new AttributeValue().withN(equalTo));
            return condition;
        }
        
        private Condition equalsString(String equalTo) {
            Condition condition = new Condition();
            condition.withComparisonOperator(ComparisonOperator.EQ);
            condition.withAttributeValueList(new AttributeValue().withS(equalTo));
            return condition;
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
    private UploadSchemaDao uploadSchemaDao;
    
    @Resource(name = "surveyMapper")
    public void setSurveyMapper(DynamoDBMapper surveyMapper) {
        this.surveyMapper = surveyMapper;
    }
    
    @Resource(name = "surveyElementMapper")
    public void setSurveyElementMapper(DynamoDBMapper surveyElementMapper) {
        this.surveyElementMapper = surveyElementMapper;
    }

    @Autowired
    public final void setUploadSchemaDao(UploadSchemaDao uploadSchemaDao) {
        this.uploadSchemaDao = uploadSchemaDao;
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
        survey.setSchemaRevision(null);
        survey.setPublished(false);
        survey.setDeleted(false);
        survey.setVersion(null);
        return saveSurvey(survey);
    }

    @Override
    public Survey publishSurvey(StudyIdentifier study, GuidCreatedOnVersionHolder keys, boolean newSchemaRev) {
        Survey survey = getSurvey(keys);
        if (survey.isDeleted()) {
            throw new EntityNotFoundException(Survey.class);
        }
        if (!survey.isPublished()) {
            // make schema from survey
            UploadSchema schema = uploadSchemaDao.createUploadSchemaFromSurvey(study, survey, newSchemaRev);

            // update survey
            survey.setPublished(true);
            survey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
            survey.setSchemaRevision(schema.getRevision());
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
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Survey.class);
        }
        if (existing.isPublished()) {
            throw new PublishedSurveyException(survey);
        }

        // copy over mutable fields
        existing.setIdentifier(survey.getIdentifier());
        existing.setName(survey.getName());
        existing.setElements(survey.getElements());

        // copy over DDB version so we can handle concurrent modification exceptions
        existing.setVersion(survey.getVersion());

        // internal bookkeeping - update modified timestamp, clear schema revision from unpublished survey
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        existing.setSchemaRevision(null);

        return saveSurvey(existing);
    }
    
    @Override
    public Survey versionSurvey(GuidCreatedOnVersionHolder keys) {
        DynamoSurvey existing = (DynamoSurvey)getSurvey(keys);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Survey.class);
        }
        DynamoSurvey copy = new DynamoSurvey(existing);
        copy.setPublished(false);
        copy.setDeleted(false);
        copy.setVersion(null);
        long time = DateUtils.getCurrentMillisFromEpoch();
        copy.setCreatedOn(time);
        copy.setModifiedOn(time);
        copy.setSchemaRevision(null);
        for (SurveyElement element : copy.getElements()) {
            element.setGuid(BridgeUtils.generateGuid());
        }
        return saveSurvey(copy);
    }

    @Override
    public void deleteSurvey(GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(keys);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Survey.class);
        }
        // If a survey has been published, you can't delete the last published version of that survey.
        // This is going to create a lot of test errors.
        if (existing.isPublished()) {
            int publishedVersionCount = new QueryBuilder().setSurvey(keys.getGuid()).isPublished().isNotDeleted().getCount();
            if (publishedVersionCount < 2) {
                throw new PublishedSurveyException(existing, "You cannot delete the last published version of a published survey.");
            }
        }
        existing.setDeleted(true);
        saveSurvey(existing);
    }

    @Override
    public void deleteSurveyPermanently(GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(keys);
        deleteAllElements(existing.getGuid(), existing.getCreatedOn());
        surveyMapper.delete(existing);
        
        // Delete the schemas as well, or they accumulate.
        try {
            StudyIdentifier studyId = new StudyIdentifierImpl(existing.getStudyIdentifier());
            uploadSchemaDao.deleteUploadSchemaById(studyId, existing.getIdentifier());
        } catch(EntityNotFoundException e) {
            // This is OK. Just means this survey wasn't published.
        }
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).isNotDeleted().getAll(true);
    }
    
    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).isNotDeleted().getOne(true);
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).isPublished().setSurvey(guid).isNotDeleted().getOne(true);
    }
    
    // secondary index query (not survey GUID) 
    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyIdentifier).isPublished().isNotDeleted().getAll(false);
        return findMostRecentVersions(surveys);
    }
    
    // secondary index query (not survey GUID)
    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyIdentifier).isNotDeleted().getAll(false);
        return findMostRecentVersions(surveys);
    }
    
    /**
     * Get a specific survey version regardless of whether or not is has been deleted. This is the only call 
     * that will return a deleted survey. With most scheduling now pointing to the most recently published 
     * version (not a specific timestamped version), this method should be rarely called.
     */
    @Override
    public Survey getSurvey(GuidCreatedOnVersionHolder keys) {
        return new QueryBuilder().setSurvey(keys.getGuid()).setCreatedOn(keys.getCreatedOn()).getOne(true);
    }
    
    /**
     * This scan gets expensive when there are many revisions. We don't know the set of unique GUIDs, so 
     * we also have to iterate over everything. 
     * @param surveys
     * @return
     */
    private List<Survey> findMostRecentVersions(List<Survey> surveys) {
        if (surveys.isEmpty()) {
            return surveys;
        }
        Map<String, Survey> map = Maps.newLinkedHashMap();
        for (Survey survey : surveys) {
            Survey stored = map.get(survey.getGuid());
            if (stored == null || survey.getCreatedOn() > stored.getCreatedOn()) {
                map.put(survey.getGuid(), survey);
            }
        }
        return ImmutableList.copyOf(map.values());
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
            // At this point, delete the elements you just created... compensating transaction
            deleteAllElements(survey.getGuid(), survey.getCreatedOn());
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
        
        List<DynamoSurveyElement> page = surveyElementMapper.query(DynamoSurveyElement.class, query);
        List<FailedBatch> failures = surveyElementMapper.batchDelete(page);
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
