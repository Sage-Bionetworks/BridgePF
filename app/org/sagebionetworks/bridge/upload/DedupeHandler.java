package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.schema.UploadSchemaKey;

/** Handler for deduping uploads. */
@Component
public class DedupeHandler implements UploadValidationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DedupeHandler.class);

    private UploadDedupeDao uploadDedupeDao;

    /** DAO for determining if an upload is a dupe. */
    @Autowired
    public void setUploadDedupeDao(UploadDedupeDao uploadDedupeDao) {
        this.uploadDedupeDao = uploadDedupeDao;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();

        try {
            // Get createdOn, healthCode, and schema.
            HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
            long createdOn = recordBuilder.getCreatedOn();
            String healthCode = recordBuilder.getHealthCode();
            UploadSchemaKey schemaKey = new UploadSchemaKey.Builder().withStudyId(recordBuilder.getStudyId())
                    .withSchemaId(recordBuilder.getSchemaId()).withRevision(recordBuilder.getSchemaRevision()).build();

            // Is this a duplicate?
            boolean isDupe = uploadDedupeDao.isDuplicate(createdOn, healthCode, schemaKey);
            if (isDupe) {
                // For now, log. Once we observe this to be running reliably, we can make it filter out dupes.
                LOG.info("DedupeHandler detected a dupe: Upload ID " + uploadId);
            } else {
                // Register the dupe, so future invocations know this is a dupe.
                uploadDedupeDao.registerUpload(createdOn, healthCode, schemaKey, uploadId);
            }
        } catch (RuntimeException ex) {
            // For now, we don't want bugs in the dedupe logic to fail uploads. Swallow exceptions, but log them so we
            // can observe them.
            LOG.error("DedupeHandler exception when handling upload ID " + uploadId + ": " + ex.getMessage(), ex);
        }
    }
}
