package org.sagebionetworks.bridge.upload;

import com.amazonaws.services.s3.model.ObjectMetadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.s3.S3Helper;

/** Uploads decrypted zip file to attachments and sets the record's raw data attachment ID appropriately. */
@Component
public class UploadRawZipHandler implements UploadValidationHandler {
    // Package-scoped for unit tests.
    static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");
    static final String RAW_ATTACHMENT_SUFFIX = "-raw.zip";

    private S3Helper s3Helper;

    /** S3 Helper, used to submit raw zip file as an attachment. */
    @Autowired
    public void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(UploadValidationContext context) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        // Upload raw data as an attachment. Attachment ID is "[uploadId]-raw.zip".
        String rawDataAttachmentId = context.getUploadId() + RAW_ATTACHMENT_SUFFIX;
        s3Helper.writeFileToS3(ATTACHMENT_BUCKET, rawDataAttachmentId, context.getDecryptedDataFile(), metadata);

        HealthDataRecord record = context.getHealthDataRecord();
        record.setRawDataAttachmentId(rawDataAttachmentId);
    }
}
