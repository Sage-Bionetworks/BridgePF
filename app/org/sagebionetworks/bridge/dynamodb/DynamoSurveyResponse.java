package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "SurveyResponse")
public class DynamoSurveyResponse implements SurveyResponse, DynamoTable {

    private String userId;
    private String guid;
    private String surveyGuid; // stored in dynamo
    private Survey survey; // constructed and returned to the consumer
    private String healthCode;
    private long startedOn;
    private long completedOn;
    private List<SurveyAnswer> answers;
    private Long version;
    
    public DynamoSurveyResponse() {
    }

    @Override
    @DynamoDBHashKey
    public String getUserId() {
        return userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    @DynamoDBRangeKey
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Override
    public Survey getSurvey() {
        return survey;
    }

    @Override
    public void setSurvey(Survey survey) {
        this.survey = survey;
    }
    
    @DynamoDBAttribute
    public String getSurveyGuid() {
        return surveyGuid;
    }
    
    public void setSurveyGuid(String surveyGuid) {
        this.surveyGuid = surveyGuid;
    }
    
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    @DynamoDBAttribute
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @Override
    public Status getStatus() {
        if (getStartedOn() == 0L && getCompletedOn() == 0L) {
            return Status.UNSTARTED;
        } else if (getCompletedOn() == 0L) {
            return Status.IN_PROGRESS;    
        }
        return Status.UNSTARTED;
    }

    @Override
    @DynamoDBAttribute
    public long getStartedOn() {
        return startedOn;
    }

    @Override
    public void setStartedOn(long startedOn) {
        this.startedOn = startedOn;
    }

    @Override
    @DynamoDBAttribute
    public long getCompletedOn() {
        return completedOn;
    }

    @Override
    public void setCompletedOn(long completedOn) {
        this.completedOn = completedOn;
    }

    @Override
    public List<SurveyAnswer> getAnswers() {
        return answers;
    }

    @Override
    public void setAnswers(List<SurveyAnswer> answers) {
        this.answers = answers;
    }

}
