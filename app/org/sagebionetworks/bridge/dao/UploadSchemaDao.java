package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.upload.UploadSchema;

public interface UploadSchemaDao {
    UploadSchema createUploadSchema(UploadSchema uploadSchema);

    UploadSchema getUploadSchema(String id);

    UploadSchema updateUploadSchema(UploadSchema uploadSchema);

    // TODO add list API to study
}
