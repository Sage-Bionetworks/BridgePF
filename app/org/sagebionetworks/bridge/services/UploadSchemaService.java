package org.sagebionetworks.bridge.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@Component
public class UploadSchemaService {
    private UploadSchemaDao uploadSchemaDao;

    @Autowired
    public void setUploadSchemaDao(UploadSchemaDao uploadSchemaDao) {
        this.uploadSchemaDao = uploadSchemaDao;
    }

    public UploadSchema createOrUpdateUploadSchema(Study study, String schemaId, UploadSchema uploadSchema) {
        // TODO: validate
        return uploadSchemaDao.createOrUpdateUploadSchema(study.getIdentifier(), schemaId, uploadSchema);
    }

    public UploadSchema getUploadSchema(Study study, String schemaId) {
        // TODO: validate
        return uploadSchemaDao.getUploadSchema(study.getIdentifier(), schemaId);
    }
}
