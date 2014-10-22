package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "SurveyResponse")
public class DynamoSurveyResponse implements SurveyResponse, DynamoTable {

    private static final String HEALTH_CODE_PROPERTY = "healthCode";
    private static final String COMPLETED_ON_PROPERTY = "completedOn";
    private static final String STARTED_ON_PROPERTY = "startedOn";
    private static final String VERSION_PROPERTY = "version";
    private static final String ANSWERS_PROPERTY = "answers";
    
    public static final DynamoSurveyResponse fromJson(JsonNode node) {
        DynamoSurveyResponse survey = new DynamoSurveyResponse();
        survey.setHealthCode(JsonUtils.asText(node, HEALTH_CODE_PROPERTY));
        survey.setVersion(JsonUtils.asLong(node, VERSION_PROPERTY));
        survey.setStartedOn(JsonUtils.asMillisSinceEpoch(node, STARTED_ON_PROPERTY));
        survey.setCompletedOn(JsonUtils.asMillisSinceEpoch(node, COMPLETED_ON_PROPERTY));
        survey.setData((ObjectNode)node);
        return survey;
    }
    
    private String guid;
    private String surveyGuid; // stored in dynamo
    private long surveyVersionedOn; // stored in dynamo
    private Survey survey; // constructed and returned to the consumer
    private String healthCode;
    private long startedOn;
    private long completedOn;
    private Long version;
    private List<SurveyAnswer> answers = Lists.newArrayList();
    
    public DynamoSurveyResponse() {
    }
    
    public DynamoSurveyResponse(String guid) {
        this.guid = guid;
    }
    
    @DynamoDBAttribute
    @JsonIgnore
    public String getSurveyGuid() {
        return surveyGuid;
    }
    public void setSurveyGuid(String surveyGuid) {
        this.surveyGuid = surveyGuid;
    }
    @DynamoDBAttribute
    @JsonIgnore
    public long getSurveyVersionedOn() {
        return surveyVersionedOn;
    }
    public void setSurveyVersionedOn(long surveyVersionedOn) {
        this.surveyVersionedOn = surveyVersionedOn;
    }
    @Override
    @JsonIgnore
    @DynamoDBAttribute
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @Override
    @DynamoDBHashKey
    public String getGuid() {
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
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
    public long getStartedOn() {
        return startedOn;
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartedOn(long startedOn) {
        this.startedOn = startedOn;
    }
    @Override
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getCompletedOn() {
        return completedOn;
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setCompletedOn(long completedOn) {
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
    public Survey getSurvey() {
        return survey;
    }
    @Override
    public void setSurvey(Survey survey) {
        this.survey = survey;
        this.surveyGuid = null;
        this.surveyVersionedOn = 0L;
        if (survey != null) {
            this.surveyGuid = survey.getGuid();
            this.surveyVersionedOn = survey.getVersionedOn();
        }
    }
    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public Status getStatus() {
        if (startedOn == 0L && completedOn == 0L) {
            return Status.UNSTARTED;
        } else if (startedOn != 0L && completedOn == 0L) {
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
        result = prime * result + ((answers == null) ? 0 : answers.hashCode());
        result = prime * result + (int) (completedOn ^ (completedOn >>> 32));
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((healthCode == null) ? 0 : healthCode.hashCode());
        result = prime * result + (int) (startedOn ^ (startedOn >>> 32));
        result = prime * result + ((surveyGuid == null) ? 0 : surveyGuid.hashCode());
        result = prime * result + (int) (surveyVersionedOn ^ (surveyVersionedOn >>> 32));
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DynamoSurveyResponse other = (DynamoSurveyResponse) obj;
        if (answers == null) {
            if (other.answers != null)
                return false;
        } else if (!answers.equals(other.answers))
            return false;
        if (completedOn != other.completedOn)
            return false;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (healthCode == null) {
            if (other.healthCode != null)
                return false;
        } else if (!healthCode.equals(other.healthCode))
            return false;
        if (startedOn != other.startedOn)
            return false;
        if (surveyGuid == null) {
            if (other.surveyGuid != null)
                return false;
        } else if (!surveyGuid.equals(other.surveyGuid))
            return false;
        if (surveyVersionedOn != other.surveyVersionedOn)
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DynamoSurveyResponse [guid=" + guid + ", surveyGuid=" + surveyGuid + ", surveyVersionedOn="
                + surveyVersionedOn + ", survey=" + survey + ", startedOn=" + startedOn + ", completedOn="
                + completedOn + ", version=" + version + ", answers=" + answers + "]";
    }
}
