package org.sagebionetworks.bridge.upload;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.HealthDataService;

@Component
public class UploadArtifactsHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(UploadArtifactsHandler.class);

    private static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");

    private HealthDataService healthDataService;
    private S3Helper s3Helper;

    @Autowired
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    @Resource(name = "s3Helper")
    public void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        String uploadId = context.getUpload().getUploadId();

        // step 1: upload health data record
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        HealthDataRecord record = recordBuilder.build();
        String recordId = healthDataService.createOrUpdateRecord(record);

        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        if (!attachmentMap.isEmpty()) {
            // step 2: upload health data attachments
            Map<String, String> attachmentIdsByFieldName = new HashMap<>();
            for (Map.Entry<String, byte[]> oneAttachment : attachmentMap.entrySet()) {
                String fieldName = oneAttachment.getKey();
                byte[] data = oneAttachment.getValue();

                // step 2a: upload attachments to metadata table
                HealthDataAttachment attachmentMetadata = healthDataService.getAttachmentBuilder()
                        .withRecordId(recordId).build();
                String attachmentId = healthDataService.createOrUpdateAttachment(attachmentMetadata);
                attachmentIdsByFieldName.put(fieldName, attachmentId);

                // step 2b: upload attachments to S3
                try {
                    s3Helper.writeBytesToS3(ATTACHMENT_BUCKET, attachmentId, data);
                } catch (IOException ex) {
                    addMessageAndWarn(context, String.format("Upload ID %s error uploading attachment for field %s: %s",
                            uploadId, fieldName, ex.getMessage()), ex);
                }
            }

            // step 3: add attachment IDs to health data record

            // Get the record back from the health data table (as it might have added new fields, like a record ID and
            // a version.
            HealthDataRecord downloadedRecord = healthDataService.getRecordById(recordId);
            HealthDataRecordBuilder updatedRecordBuilder = healthDataService.getRecordBuilder().copyOf(
                    downloadedRecord);

            // For code hygiene, make a deep copy of the dataMap. This shouldn't be too expensive, since all the large
            // fields are shunted off into attachments.
            // IosSchemaValidationHandler guarantees getData() to return an ObjectNode.
            ObjectNode downloadedDataMap = (ObjectNode) downloadedRecord.getData();
            ObjectNode updatedDataMap = downloadedDataMap.deepCopy();

            // write attachment fields and IDs (foreign keys / S3 keys) to the updated data map
            for (Map.Entry<String, String> oneAttachmentId : attachmentIdsByFieldName.entrySet()) {
                String fieldName = oneAttachmentId.getKey();
                String attachmentId = oneAttachmentId.getValue();
                updatedDataMap.put(fieldName, attachmentId);
            }

            // Write the updated data map to the record. Write the record to the record table.
            updatedRecordBuilder.withData(updatedDataMap);
            HealthDataRecord updatedRecord = updatedRecordBuilder.build();
            healthDataService.createOrUpdateRecord(updatedRecord);
        }
    }

    private static void addMessageAndWarn(UploadValidationContext context, String message, Throwable ex) {
        context.addMessage(message);
        if (ex != null) {
            logger.warn(message, ex);
        } else {
            logger.warn(message);
        }
    }
}
