package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoSurveyDao implements SurveyDao {

    private static Logger logger = LoggerFactory.getLogger(DynamoSurveyDao.class);

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoSurvey.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Override
    public Survey createSurvey(Survey survey) {
        if (!isNew(survey)) {
            throw new IllegalArgumentException("Cannot create an already created survey");
        } else if (!isValid(survey)) {
            throw new IllegalArgumentException("Survey is invalid (most likely missing required fields)");
        }
        survey.setGuid(generateId());
        survey.setVersionedOn(DateTime.now(DateTimeZone.UTC).getMillis());
        List<SurveyQuestion> questions = nomod(survey.getQuestions());
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setGuid(generateId());
            question.setOrder(i);
            question.setSurveyGuid(survey.getGuid());
            mapper.save(question);
        }
        logger.info(survey.toString());
        mapper.save(survey);
        return survey;
    }

    @Override
    public Survey updateSurvey(Survey survey) {
        if (isNew(survey)) {
            throw new IllegalArgumentException("Cannot update a new survey before you create it");
        } else if (!isValid(survey)) {
            throw new IllegalArgumentException("Survey is invalid (most likely missing required fields)");
        }
        Survey existingSurvey = getSurvey(survey.getGuid());
        if (existingSurvey.isPublished()) {
            throw new IllegalArgumentException("Cannot update a published survey");
        }
        // Enforce these, they are readonly
        survey.setPublished(existingSurvey.isPublished());
        survey.setVersionedOn(existingSurvey.getVersionedOn());
        
        List<SurveyQuestion> questions = nomod(survey.getQuestions());
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setSurveyGuid(existingSurvey.getGuid());
            question.setOrder(i);
            // A new question was added to the survey.
            if (question.getGuid() == null) {
                question.setGuid(generateId());
            }
            mapper.save(question);
        }
        logger.info(survey.toString());
        mapper.save(survey);
        return survey;
    }
    
    @Override
    public Survey versionSurvey(String surveyGuid) {
        Survey existing = getSurvey(surveyGuid);
        Survey copy = new DynamoSurvey(existing);
        copy.setGuid(null);
        copy.setPublished(false);
        copy.setVersion(null);
        copy.setVersionedOn(0L);
        for (SurveyQuestion question : nomod(copy.getQuestions())) {
            question.setGuid(null);
        }
        return createSurvey(copy);
    }

    @Override
    public List<Survey> getSurveys(Study study) {
        throw new UnsupportedOperationException("getSurveys() not implemented");
    }

    @Override
    public Survey getSurvey(String surveyGuid) {
        // TODO: Why not mapper.load?
        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.EQ).withAttributeValueList(
                new AttributeValue().withS(surveyGuid));
        DynamoDBQueryExpression<DynamoSurvey> queryExpression = new DynamoDBQueryExpression<DynamoSurvey>()
                .withRangeKeyCondition("guid", condition);
        
        QueryResultPage<DynamoSurvey> page = mapper.queryPage(DynamoSurvey.class, queryExpression);
        if (page == null || page.getResults().size() == 0) {
            return null;
        }
        return page.getResults().get(0);
    }

    @Override
    public void deleteSurvey(String surveyGuid) {
        Survey existing = getSurvey(surveyGuid);
        if (existing.isPublished()) {
            // something has to happen here, this could be bad. Might need to publish another survey?
        }
        mapper.batchDelete(existing.getQuestions());
        mapper.delete(existing);
    }
    
    private String generateId() {
        return UUID.randomUUID().toString();
    }
    
    private <T> List<T> nomod(List<T> list) {
        return Collections.unmodifiableList(list);
    }
    
    private boolean isNew(Survey survey) {
        if (survey.getGuid() != null || survey.isPublished() || survey.getVersion() != null || survey.getVersionedOn() != 0L) {
            return false;
        }
        for (SurveyQuestion question : nomod(survey.getQuestions())) {
            if (question.getGuid() != null) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isValid(Survey survey) {
        if (StringUtils.isBlank(survey.getIdentifier())) {
            return false;
        }
        for (SurveyQuestion question : nomod(survey.getQuestions())) {
            if (StringUtils.isBlank(question.getIdentifier())) {
                return false;
            }
        }
        return true;
    }

}
