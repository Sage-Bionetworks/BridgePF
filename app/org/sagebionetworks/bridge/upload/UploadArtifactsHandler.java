package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

@Component
public class UploadArtifactsHandler implements UploadValidationHandler {
    private HealthDataService healthDataService;

    @Autowired
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        String uploadId = context.getUploadId();

        // Upload health data record. Set record ID to be the same as upload ID.
        HealthDataRecord record = context.getHealthDataRecord();
        record.setId(uploadId);
        String recordId = healthDataService.createOrUpdateRecord(record);
        context.setRecordId(recordId);
    }
}
