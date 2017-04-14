package org.sagebionetworks.bridge.models.sharedmodules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * This struct contains the results of importing a shared module into the local study, including what type of module it
 * is, and its key params (schema ID/revision or survey GUID/createdOn) in the local study.
 */
@BridgeTypeName("SharedModuleImportStatus")
public class SharedModuleImportStatus implements BridgeEntity {
    private final String schemaId;
    private final Integer schemaRevision;
    private final Long surveyCreatedOn;
    private final String surveyGuid;

    /** Constructs status object with a schema. */
    public SharedModuleImportStatus(String schemaId, int schemaRevision) {
        checkNotNull(schemaId, "schema ID must be specified");
        checkArgument(StringUtils.isNotBlank(schemaId), "schema ID must be specified");
        checkArgument(schemaRevision > 0, "schema revision must be positive");

        this.schemaId = schemaId;
        this.schemaRevision = schemaRevision;
        this.surveyCreatedOn = null;
        this.surveyGuid = null;
    }

    /** Constructs status object with a survey. */
    public SharedModuleImportStatus(String surveyGuid, long surveyCreatedOn) {
        checkNotNull(surveyGuid, "survey GUID must be specified");
        checkArgument(StringUtils.isNotBlank(surveyGuid), "survey GUID must be specified");
        checkArgument(surveyCreatedOn > 0, "survey createdOn must be positive");

        this.schemaId = null;
        this.schemaRevision = null;
        this.surveyCreatedOn = surveyCreatedOn;
        this.surveyGuid = surveyGuid;
    }

    /** Whether this module is a schema or a survey. Throws if the module is neither. */
    public SharedModuleType getModuleType() {
        if (getSchemaId() != null) {
            return SharedModuleType.SCHEMA;
        } else if (getSurveyGuid() != null) {
            return SharedModuleType.SURVEY;
        } else {
            // If we ever hit this code block, something has gone terribly terribly wrong.
            throw new IllegalStateException("Unexpected error: Somehow, module is neither a schema nor survey.");
        }
    }

    /** Schema ID, if this module is a schema. */
    public String getSchemaId() {
        return schemaId;
    }

    /** Schema revision, if this module is a schema. */
    public Integer getSchemaRevision() {
        return schemaRevision;
    }

    /** Survey created-on timestamp (in epoch milliseconds), if this module is a survey. */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getSurveyCreatedOn() {
        return surveyCreatedOn;
    }

    /** Survey GUID, if this module is a survey. */
    public String getSurveyGuid() {
        return surveyGuid;
    }
}
