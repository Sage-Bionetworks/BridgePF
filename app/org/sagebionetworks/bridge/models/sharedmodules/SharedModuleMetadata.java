package org.sagebionetworks.bridge.models.sharedmodules;

import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.hibernate.HibernateSharedModuleMetadata;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/** Metadata for shared modules. */
@BridgeTypeName("SharedModuleMetadata")
@JsonDeserialize(as = HibernateSharedModuleMetadata.class)
public interface SharedModuleMetadata extends BridgeEntity {
    /** Convenience method for creating a SharedModuleMetadata using a concrete implementation. */
    static SharedModuleMetadata create() {
        return new HibernateSharedModuleMetadata();
    }

    /** Module ID. */
    String getId();

    /** @see #getId */
    void setId(String id);

    /** True if usage of this module is restricted by license or copyright. */
    boolean isLicenseRestricted();

    /** @see #isLicenseRestricted */
    void setLicenseRestricted(boolean licenseRestricted);

    /** Whether this module is a schema or a survey. Throws if the module is neither. */
    default SharedModuleType getModuleType() {
        if (getSchemaId() != null) {
            return SharedModuleType.SCHEMA;
        } else if (getSurveyGuid() != null) {
            return SharedModuleType.SURVEY;
        } else {
            throw new InvalidEntityException("Shared module has neither schema nor survey");
        }
    }

    /** User-friendly module name. */
    String getName();

    /** @see #getName */
    void setName(String name);

    /** User-friendly descriptive notes for the module. */
    String getNotes();

    /** @see #getNotes */
    void setNotes(String notes);

    /** Which OS (iOS, Android, etc) this module applies to, if the module is OS-specific. */
    String getOs();

    /** @see #getOs */
    void setOs(String os);

    /**
     * Flag that marks the module as published. A published module versions cannot be modified. However, another
     * version of the same module can be created for continued editing.
     */
    boolean isPublished();

    /** @see #isPublished */
    void setPublished(boolean published);

    /** Schema ID, if this module is a schema. */
    String getSchemaId();

    /** @see #getSchemaId */
    void setSchemaId(String schemaId);

    /** Schema revision, if this module is a schema. */
    Integer getSchemaRevision();

    /** @see #getSchemaRevision */
    void setSchemaRevision(Integer schemaRevision);

    /** Survey created-on timestamp (in epoch milliseconds), if this module is a survey. */
    Long getSurveyCreatedOn();

    /** @see #getSurveyCreatedOn */
    void setSurveyCreatedOn(Long surveyCreatedOn);

    /** Survey GUID, if this module is a survey. */
    String getSurveyGuid();

    /** @see #getSurveyGuid */
    void setSurveyGuid(String surveyGuid);

    /** Module tags, used for querying and filtering. */
    Set<String> getTags();

    /** @see #getTags */
    void setTags(Set<String> tags);
    
    /** Has this version of the module metadata been logically deleted? */
    boolean isDeleted();
    
    /** @see #isDeleted */
    void setDeleted(boolean deleted);

    /** Module version. */
    int getVersion();

    /** @see #getVersion */
    void setVersion(int version);
}
