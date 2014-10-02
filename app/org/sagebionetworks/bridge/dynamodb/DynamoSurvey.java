package org.sagebionetworks.bridge.dynamodb;

import java.util.Iterator;
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "Survey")
public class DynamoSurvey implements Survey, DynamoTable {
    
    private static final String VERSION_FIELD = "version";
    private static final String NAME_FIELD = "name";
    private static final String IDENTIFIER_FIELD = "identifier";
    private static final String QUESTIONS_FIELD = "questions";
    
    public static final DynamoSurvey fromJson(JsonNode node) {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setVersion( JsonUtils.asLong(node, VERSION_FIELD) );
        survey.setName( JsonUtils.asText(node, NAME_FIELD) );
        survey.setIdentifier( JsonUtils.asText(node, IDENTIFIER_FIELD) );
        ArrayNode questionsNode = JsonUtils.asArrayNode(node,  QUESTIONS_FIELD);
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
    
    public DynamoSurvey(DynamoSurvey survey) {
        this();
        setStudyKey(survey.getStudyKey());
        setGuid(survey.getGuid());
        setVersionedOn(survey.getVersionedOn());
        setModifiedOn(survey.getModifiedOn());
        setVersion(survey.getVersion());
        setName(survey.getName());
        setIdentifier(survey.getIdentifier());
        setPublished(survey.isPublished());
        for (Iterator<SurveyQuestion> i = survey.getQuestions().iterator(); i.hasNext();){
            DynamoSurveyQuestion q = (DynamoSurveyQuestion)i.next();
            questions.add( new DynamoSurveyQuestion(q) );
        }
    }

    @Override
    @DynamoDBAttribute
    @JsonIgnore
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + (int) (modifiedOn ^ (modifiedOn >>> 32));
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (published ? 1231 : 1237);
        result = prime * result + ((questions == null) ? 0 : questions.hashCode());
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        result = prime * result + (int) (versionedOn ^ (versionedOn >>> 32));
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
        DynamoSurvey other = (DynamoSurvey) obj;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        if (modifiedOn != other.modifiedOn)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (published != other.published)
            return false;
        if (questions == null) {
            if (other.questions != null)
                return false;
        } else if (!questions.equals(other.questions))
            return false;
        if (studyKey == null) {
            if (other.studyKey != null)
                return false;
        } else if (!studyKey.equals(other.studyKey))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        if (versionedOn != other.versionedOn)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "DynamoSurvey [studyKey=" + studyKey + ", guid=" + guid + ", versionedOn=" + versionedOn
                + ", modifiedOn=" + modifiedOn + ", version=" + version + ", name=" + name + ", identifier="
                + identifier + ", published=" + published + ", questions=" + questions + "]";
    }
}
