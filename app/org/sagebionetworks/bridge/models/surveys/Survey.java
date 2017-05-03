package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurvey.class)
@BridgeTypeName("Survey")
public interface Survey extends GuidCreatedOnVersionHolder, BridgeEntity  {
    /** Convenience method for creating an instance of Survey using a concrete implementation. */
    static Survey create() {
        return new DynamoSurvey();
    }

    String getStudyIdentifier();
    void setStudyIdentifier(String studyIdentifier);
    
    String getGuid();
    void setGuid(String guid);
    
    long getCreatedOn();
    void setCreatedOn(long createdOn);
    
    long getModifiedOn();
    void setModifiedOn(long modifiedOn);

    String getCopyrightNotice();
    void setCopyrightNotice(String copyrightNotice);

    /** Module ID, if this survey was imported from a shared module. */
    String getModuleId();

    /** @see #getModuleId */
    void setModuleId(String moduleId);

    /** Module version, if this survey was imported from a shared module. */
    Integer getModuleVersion();

    /** @see #getModuleVersion */
    void setModuleVersion(Integer moduleVersion);

    Long getVersion();
    void setVersion(Long version);
    
    String getName();
    void setName(String name);

    String getIdentifier();
    void setIdentifier(String identifier);

    boolean isPublished();
    void setPublished(boolean published);

    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    /**
     * Gets the upload schema revision that corresponds to this survey. See
     * {@link org.sagebionetworks.bridge.models.upload.UploadSchema#getRevision} for more details.
     */
    Integer getSchemaRevision();

    /** @see #getSchemaRevision */
    void setSchemaRevision(Integer schemaRevision);

    List<SurveyElement> getElements();
    void setElements(List<SurveyElement> elements);
    
    List<SurveyQuestion> getUnmodifiableQuestionList();
    
}
