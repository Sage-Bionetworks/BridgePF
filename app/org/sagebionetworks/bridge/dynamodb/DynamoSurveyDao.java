package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Study;
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
    }

    @Override
    public Survey updateSurvey(Survey survey) {
        if (isNew(survey)) {
            throw new BridgeServiceException("Cannot update a new survey before you create it", 400);
        } else if (!isValid(survey)) {
            throw new BridgeServiceException("Survey is invalid (most likely missing required fields)", 400);
        }
        Survey existingSurvey = getSurvey(survey.getStudyKey(), survey.getGuid());
        if (existingSurvey.isPublished()) {
            throw new BridgeServiceException("Cannot update a published survey", 400);
        }
        // Enforce these, they are readonly
        survey.setPublished(existingSurvey.isPublished());
        survey.setVersionedOn(existingSurvey.getVersionedOn());
        
        deleteAllQuestions(survey.getGuid());
        List<SurveyQuestion> questions = nomod(survey.getQuestions());
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            question.setSurveyGuid(existingSurvey.getGuid());
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
        for (DynamoSurveyQuestion question :  getAllQuestions(surveyGuid)) {
            surveyQuestionMapper.delete(question);
        }
    }
    
    private List<DynamoSurveyQuestion> getAllQuestions(String surveyGuid) {
        DynamoSurveyQuestion template = new DynamoSurveyQuestion();
        template.setSurveyGuid(surveyGuid);
        
        DynamoDBQueryExpression<DynamoSurveyQuestion> queryExpression = new DynamoDBQueryExpression<DynamoSurveyQuestion>()
                .withHashKeyValues(template);
        
        QueryResultPage<DynamoSurveyQuestion> page = surveyQuestionMapper.queryPage(DynamoSurveyQuestion.class,
                queryExpression);
        return page.getResults();
    }
    
    @Override
    public Survey versionSurvey(String studyKey, String surveyGuid) {
        Survey existing = getSurvey(studyKey, surveyGuid);
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
    public Survey getSurvey(String studyKey, String surveyGuid) {
        Survey survey = new DynamoSurvey();
        survey.setStudyKey(studyKey);
        survey.setGuid(surveyGuid);
        
        survey = surveyMapper.load(survey);
        if (survey == null) {
            throw new IllegalArgumentException("The survey was null, studyKey: " + studyKey + ", surveyGuid: " + surveyGuid);
        }
        
        List<DynamoSurveyQuestion> dynoSurveyQuestions = getAllQuestions(surveyGuid);
        if (dynoSurveyQuestions == null) {
            throw new IllegalArgumentException("The survey questions were null");
        }
        List<SurveyQuestion> questions = Lists.newArrayListWithCapacity(dynoSurveyQuestions.size());
        for (DynamoSurveyQuestion question : getAllQuestions(surveyGuid)) {
            questions.add((SurveyQuestion)question);
        }
        survey.setQuestions(questions);
        
        return survey;
    }

    @Override
    public void deleteSurvey(String studyKey, String surveyGuid) {
        Survey existing = getSurvey(studyKey, surveyGuid);
        if (existing.isPublished()) {
            // something has to happen here, this could be bad. Might need to publish another survey?
        }
        surveyMapper.batchDelete(existing.getQuestions());
        surveyMapper.delete(existing);
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
        if (StringUtils.isBlank(survey.getIdentifier()) || StringUtils.isBlank(survey.getStudyKey())) {
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
