package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.upload.UploadSchema;

public interface UploadSchemaDao {
    UploadSchema createOrUpdateUploadSchema(String studyId, String schemaId, UploadSchema uploadSchema);

    UploadSchema getUploadSchema(String studyId, String schemaId);

    // TODO add list API to study
}
