package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementConstants;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "Survey")
public class DynamoSurvey implements Survey {
    
    private static final String VERSION_FIELD = "version";
    private static final String NAME_FIELD = "name";
    private static final String IDENTIFIER_FIELD = "identifier";
    private static final String ELEMENTS_FIELD = "elements";
    
    public static final DynamoSurvey fromJson(JsonNode node) {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setVersion( JsonUtils.asLong(node, VERSION_FIELD) );
        survey.setName( JsonUtils.asText(node, NAME_FIELD) );
        survey.setIdentifier( JsonUtils.asText(node, IDENTIFIER_FIELD) );
        List<SurveyElement> elements = JsonUtils.asSurveyElementsArray(node, ELEMENTS_FIELD);
        survey.setElements(elements);
        return survey;
    }

    private String studyKey;
    private String guid;
    private long createdOn;
    private long modifiedOn;
    private Long version;
    private String name;
    private String identifier;
    private boolean published;
    private List<SurveyElement> elements;
    
    public DynamoSurvey() {
        this.elements = Lists.newArrayList();
    }
    
    public DynamoSurvey(String guid, long createdOn) {
        this();
        setGuid(guid);
        setCreatedOn(createdOn);
    }
    
    public DynamoSurvey(DynamoSurvey survey) {
        this();
        setStudyIdentifier(survey.getStudyIdentifier());
        setGuid(survey.getGuid());
        setCreatedOn(survey.getCreatedOn());
        setModifiedOn(survey.getModifiedOn());
        setVersion(survey.getVersion());
        setName(survey.getName());
        setIdentifier(survey.getIdentifier());
        setPublished(survey.isPublished());
        for (SurveyElement element : survey.getElements()) {
            elements.add(SurveyElementFactory.fromDynamoEntity(element));
        }
    }
    
    @Override
    @DynamoDBAttribute(attributeName = "studyKey")
    @JsonIgnore
    public String getStudyIdentifier() {
        return studyKey;
    }

    @Override
    public void setStudyIdentifier(String studyKey) {
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
    @DynamoDBRangeKey(attributeName="versionedOn")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
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
    public List<SurveyElement> getElements() {
        return elements;
    }
    
    @Override
    @DynamoDBIgnore
    @JsonProperty("questions")
    public List<SurveyQuestion> getUnmodifiableQuestionList() {
        ImmutableList.Builder<SurveyQuestion> builder = new ImmutableList.Builder<SurveyQuestion>();
        for (SurveyElement element : elements) {
            if (SurveyElementConstants.SURVEY_QUESTION_TYPE.equals(element.getType())) {
                builder.add((SurveyQuestion)element);
            }
        }
        return builder.build();
    }

    @Override
    public void setElements(List<SurveyElement> elements) {
        this.elements = elements;
    }
    
    @Override
    public boolean keysEqual(GuidCreatedOnVersionHolder keys) {
        return (keys != null && keys.getGuid().equals(guid) && keys.getCreatedOn() == createdOn);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (createdOn ^ (createdOn >>> 32));
        result = prime * result + ((elements == null) ? 0 : elements.hashCode());
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + (int) (modifiedOn ^ (modifiedOn >>> 32));
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (published ? 1231 : 1237);
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
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
        DynamoSurvey other = (DynamoSurvey) obj;
        if (createdOn != other.createdOn)
            return false;
        if (elements == null) {
            if (other.elements != null)
                return false;
        } else if (!elements.equals(other.elements))
            return false;
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
        return true;
    }

    @Override
    public String toString() {
        return "DynamoSurvey [studyKey=" + studyKey + ", guid=" + guid + ", createdOn=" + createdOn + ", modifiedOn="
                + modifiedOn + ", version=" + version + ", name=" + name + ", identifier=" + identifier
                + ", published=" + published + ", elements=" + elements + "]";
    }
}
