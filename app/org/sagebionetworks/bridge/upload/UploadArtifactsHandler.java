package org.sagebionetworks.bridge.upload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

/**
 * This handler submits the completed Health Data Record to the Health Data Service. This used to do more, but the
 * extra bookkeeping on attachments was deemed unnecessary.
 */
@Component
public class UploadArtifactsHandler implements UploadValidationHandler {
    private HealthDataService healthDataService;

    /** Health Data Service, used to submit health data records. */
    @Autowired
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(UploadValidationContext context) {
        String uploadId = context.getUploadId();

        // Upload health data record. Set record ID to be the same as upload ID.
        HealthDataRecord record = context.getHealthDataRecord();
        record.setId(uploadId);

        // If the record already exists (for example, this is a redrive), we need to set the version attribute properly
        // so we overwrite the old record properly.
        HealthDataRecord oldRecord = healthDataService.getRecordById(uploadId);
        if (oldRecord != null) {
            record.setVersion(oldRecord.getVersion());
        }

        String recordId = healthDataService.createOrUpdateRecord(record);
        context.setRecordId(recordId);
    }
}
