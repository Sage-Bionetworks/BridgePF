package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "Survey")
public class DynamoSurvey implements Survey, DynamoTable {
    
    private static final String VERSION = "version";
    private static final String NAME = "name";
    private static final String IDENTIFIER = "identifier";
    private static final String QUESTIONS = "questions";
    
    public static final DynamoSurvey fromJson(JsonNode node) {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setVersion( JsonUtils.asLong(node, VERSION) );
        survey.setName( JsonUtils.asText(node, NAME) );
        survey.setIdentifier( JsonUtils.asText(node, IDENTIFIER) );
        ArrayNode questionsNode = JsonUtils.asArrayNode(node,  QUESTIONS);
        if (questionsNode != null) {
            for (JsonNode questionNode : questionsNode) {
                SurveyQuestion question = DynamoSurveyQuestion.fromJson(questionNode);
                survey.getQuestions().add(question);
            }
        }
        return survey;
    }

    private String studyKey;
    private String guid;
    private long versionedOn;
    private long modifiedOn;
    private Long version;
    private String name;
    private String identifier;
    private boolean published;
    private List<SurveyQuestion> questions;
    
    public DynamoSurvey() {
        this.questions = Lists.newArrayList();
    }
    
    public DynamoSurvey(String guid, long versionedOn) {
        this();
        setGuid(guid);
        setVersionedOn(versionedOn);
    }
    
    public DynamoSurvey(Survey survey) {
        this();
        setStudyKey(survey.getStudyKey());
        setGuid(survey.getGuid());
        setVersionedOn(survey.getVersionedOn());
        setModifiedOn(survey.getModifiedOn());
        setVersion(survey.getVersion());
        setName(survey.getName());
        setIdentifier(survey.getIdentifier());
        setPublished(survey.isPublished());
        for (SurveyQuestion question : survey.getQuestions()) {
            questions.add( new DynamoSurveyQuestion(question) );
        }
    }

    @Override
    @DynamoDBAttribute
    public String getStudyKey() {
        return studyKey;
    }

    @Override
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
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
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getVersionedOn() {
        return versionedOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setVersionedOn(long versionedOn) {
        this.versionedOn = versionedOn;
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
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getModifiedOn() {
        return modifiedOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    @Override
    @DynamoDBAttribute
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    @DynamoDBAttribute
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    @DynamoDBAttribute
    public boolean isPublished() {
        return published;
    }

    @Override
    public void setPublished(boolean published) {
        this.published = published;
    }

    @Override
    @DynamoDBIgnore
    public List<SurveyQuestion> getQuestions() {
        return questions;
    }

    @Override
    public void setQuestions(List<SurveyQuestion> questions) {
        this.questions = questions;
    }
    
    @Override
    @DynamoDBIgnore
    public String getType() {
        return "Survey";
    }

    @Override
    public String toString() {
        return "DynamoSurvey [studyKey=" + studyKey + ", guid=" + guid + ", versionedOn=" + versionedOn
                + ", modifiedOn=" + modifiedOn + ", version=" + version + ", name=" + name + ", identifier="
                + identifier + ", published=" + published + ", questions=" + questions + "]";
    }
}
