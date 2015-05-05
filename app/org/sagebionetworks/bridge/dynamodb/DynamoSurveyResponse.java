package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "SurveyResponse2")
public final class DynamoSurveyResponse implements SurveyResponse {

    private static final String ANSWERS_PROPERTY = "answers";
    
    private String healthCode;
    private String identifier;
    
    // These three are stored in Dynamo.
    private String surveyKey;
    
    private Long startedOn;
    private Long completedOn;
    private Long version;
    private List<SurveyAnswer> answers = Lists.newArrayList();
    
    @Override
    @JsonIgnore
    @DynamoDBHashKey
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    
    // Main range key: identifier
    @Override
    @DynamoDBRangeKey
    public String getIdentifier() {
        return identifier;
    }
    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    @JsonIgnore
    @DynamoDBAttribute
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "surveyKey-index")
    public String getSurveyKey() {
        return surveyKey;
    }
    public void setSurveyKey(String surveyKey) {
        this.surveyKey = surveyKey;
    }
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public String getSurveyGuid() {
        try {
            return (surveyKey != null) ? surveyKey.split(":")[0] : null;    
        } catch(Exception e) {
            return null;
        }
    }
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public long getSurveyCreatedOn() {
        try {
            return (surveyKey != null) ? Long.parseLong(surveyKey.split(":")[1]) : null;    
        } catch(Exception e) {
            return 0L;
        }
    }
    @Override
    @DynamoDBVersionAttribute
    @JsonIgnore
    public Long getVersion() {
        return version;
    }
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    @Override
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getStartedOn() {
        return startedOn;
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }
    @Override
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getCompletedOn() {
        return completedOn;
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setCompletedOn(Long completedOn) {
        this.completedOn = completedOn;
    }
    @Override
    @DynamoDBIgnore
    public List<SurveyAnswer> getAnswers() {
        return answers;
    }
    @Override
    public void setAnswers(List<SurveyAnswer> answers) {
        this.answers = answers;
    }
    @Override
    @DynamoDBIgnore
    public Status getStatus() {
        if (startedOn == null && completedOn == null) {
            return Status.UNSTARTED;
        } else if (startedOn != null && completedOn == null) {
            return Status.IN_PROGRESS;    
        }
        return Status.FINISHED;
    }
    
    @JsonIgnore
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public ObjectNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.set(ANSWERS_PROPERTY, BridgeObjectMapper.get().valueToTree(answers));
        return data;
    }
    public void setData(ObjectNode data) {
        this.answers = JsonUtils.asEntityList(data, ANSWERS_PROPERTY, SurveyAnswer.class);
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(healthCode);
        result = prime * result + Objects.hashCode(identifier);
        result = prime * result + Objects.hashCode(surveyKey);
        result = prime * result + Objects.hashCode(startedOn);
        result = prime * result + Objects.hashCode(completedOn);
        result = prime * result + Objects.hashCode(answers);
        result = prime * result + Objects.hashCode(version);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoSurveyResponse other = (DynamoSurveyResponse) obj;
        return (Objects.equals(healthCode, other.healthCode) && Objects.equals(identifier, other.identifier)
            && Objects.equals(surveyKey, other.surveyKey) && Objects.equals(startedOn, other.startedOn) 
            && Objects.equals(completedOn, other.completedOn) && Objects.equals(answers, other.answers)
            && Objects.equals(version, other.version));
    }

    @Override
    public String toString() {
        return String.format("DynamoSurveyResponse [identifier=%s, surveyKey=%s, startedOn=%s, completedOn=%s, version=%s, answers=%s, version=%s]", 
                identifier, surveyKey, startedOn, completedOn, version, answers, version);
    }
}
