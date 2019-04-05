package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Objects;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementConstants;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

@DynamoDBTable(tableName = "Survey")
@JsonFilter("filter")
public class DynamoSurvey implements Survey {
    private String studyKey;
    private String guid;
    private long createdOn;
    private long modifiedOn;
    private String copyrightNotice;
    private String moduleId;
    private Integer moduleVersion;
    private Long version;
    private String name;
    private String identifier;
    private boolean published;
    private boolean deleted;
    private Integer schemaRevision;
    private List<SurveyElement> elements;

    public DynamoSurvey() {
        this.elements = Lists.newArrayList();
    }

    public DynamoSurvey(String guid, long createdOn) {
        this();
        setGuid(guid);
        setCreatedOn(createdOn);
    }

    /**
     * This copy constructor copies all fields, but it also converts base DynamoSurveyElements to their proper
     * corresponding subclasses (DynamoSurveyQuestion or DynamoSurveyInfoScreen). This is done because Dynamo DB has
     * no concept of inheritance, so we need to re-construct the subclasses.
     */
    public DynamoSurvey(DynamoSurvey survey) {
        this();
        setStudyIdentifier(survey.getStudyIdentifier());
        setGuid(survey.getGuid());
        setCreatedOn(survey.getCreatedOn());
        setModifiedOn(survey.getModifiedOn());
        setCopyrightNotice(survey.getCopyrightNotice());
        setModuleId(survey.getModuleId());
        setModuleVersion(survey.getModuleVersion());
        setVersion(survey.getVersion());
        setName(survey.getName());
        setIdentifier(survey.getIdentifier());
        setPublished(survey.isPublished());
        setDeleted(survey.isDeleted());
        setSchemaRevision(survey.getSchemaRevision());
        for (SurveyElement element : survey.getElements()) {
            elements.add(SurveyElementFactory.fromDynamoEntity(element));
        }
    }

    @Override
    @JsonIgnore
    @DynamoDBAttribute(attributeName = "studyKey")
    @DynamoDBIndexHashKey(attributeName = "studyKey", globalSecondaryIndexName = "studyKey-identifier-index")
    @DynamoProjection(projectionType = ProjectionType.ALL, globalSecondaryIndexName = "studyKey-identifier-index")
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
    @DynamoDBRangeKey(attributeName = "versionedOn")
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
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
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getModifiedOn() {
        return modifiedOn;
    }

    @Override
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    @DynamoDBAttribute
    @Override public String getCopyrightNotice() {
        return copyrightNotice;
    }

    @Override public void setCopyrightNotice(String copyrightNotice) {
        this.copyrightNotice = copyrightNotice;
    }

    /** {@inheritDoc} */
    @Override
    public String getModuleId() {
        return moduleId;
    }

    /** {@inheritDoc} */
    @Override
    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getModuleVersion() {
        return moduleVersion;
    }

    /** {@inheritDoc} */
    @Override
    public void setModuleVersion(Integer moduleVersion) {
        this.moduleVersion = moduleVersion;
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
    @DynamoDBIndexRangeKey(attributeName = "identifier", globalSecondaryIndexName = "studyKey-identifier-index")
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
    @DynamoDBAttribute
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public Integer getSchemaRevision() {
        return schemaRevision;
    }

    @Override
    public void setSchemaRevision(Integer schemaRevision) {
        this.schemaRevision = schemaRevision;
    }

    @Override
    @DynamoDBIgnore
    public List<SurveyElement> getElements() {
        return elements;
    }

    @Override
    @DynamoDBIgnore
    @JsonIgnore
    public List<SurveyQuestion> getUnmodifiableQuestionList() {
        ImmutableList.Builder<SurveyQuestion> builder = new ImmutableList.Builder<>();
        for (SurveyElement element : elements) {
            if (SurveyElementConstants.SURVEY_QUESTION_TYPE.equals(element.getType())) {
                builder.add((SurveyQuestion) element);
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
    public final int hashCode() {
        return Objects.hash(studyKey, guid, createdOn, modifiedOn, copyrightNotice, moduleId, moduleVersion, version,
                name, identifier,
                published, deleted, schemaRevision, elements);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || !(obj instanceof DynamoSurvey)) {
            return false;
        }
        final DynamoSurvey that = (DynamoSurvey) obj;
        return Objects.equals(this.studyKey, that.studyKey)
                && Objects.equals(this.guid, that.guid)
                && Objects.equals(this.createdOn, that.createdOn)
                && Objects.equals(this.modifiedOn, that.modifiedOn)
                && Objects.equals(this.copyrightNotice, that.copyrightNotice)
                && Objects.equals(this.moduleId, that.moduleId)
                && Objects.equals(this.moduleVersion, that.moduleVersion)
                && Objects.equals(this.version, that.version)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.identifier, that.identifier)
                && Objects.equals(this.published, that.published)
                && Objects.equals(this.deleted, that.deleted)
                && Objects.equals(this.schemaRevision, that.schemaRevision)
                && Objects.equals(this.elements, that.elements);
    }

    @Override
    public String toString() {
        return String.format(
                "DynamoSurvey [studyKey=%s, guid=%s, createdOn=%s, modifiedOn=%s, copyrightNotice=%s, moduleId=%s, " +
                        "moduleVersion=%d, version=%s, name=%s, identifier=%s, published=%s, deleted=%s, " +
                        "schemaRevision=%s, elements=%s]", studyKey, guid, createdOn, modifiedOn, copyrightNotice,
                moduleId, moduleVersion, version, name, identifier, published, deleted, schemaRevision, elements);
    }
}
