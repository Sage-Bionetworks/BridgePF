package org.sagebionetworks.bridge.services.backfill;

import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Re-drives upload validation for a list of record IDs. These record IDs live in a file called
 * upload-validation-backfill-recordIds in an S3 bucket called org-sagebridge-backfill-prod (replace prod) with your
 * env name. The record IDs are then queried against the healthDataDao to get the upload ID.
 * </p>
 * <p>
 * File format is one record ID per line.
 * </p>
 */
@Component("uploadValidationByRecordIdBackfill")
public class UploadValidationByRecordIdBackfill extends UploadValidationBackfill {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationByRecordIdBackfill.class);

    private static final String RECORD_ID_BUCKET = "org-sagebridge-backfill-" + BridgeConfigFactory.getConfig()
            .getEnvironment().name().toLowerCase();
    private static final String RECORD_ID_FILENAME = "upload-validation-backfill-recordIds";

    private HealthDataDao healthDataDao;

    /** Health data DAO, used to get the upload ID from the record ID. */
    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /** @{inheritDoc} */
    @Override
    protected List<String> getUploadIdList(BackfillTask task, BackfillCallback callback) throws IOException {
        // get file of list of record IDs
        S3Object recordIdFile = getS3Client().getObject(RECORD_ID_BUCKET, RECORD_ID_FILENAME);
        List<String> recordIdList;
        try (BufferedReader recordIdReader = new BufferedReader(new InputStreamReader(recordIdFile.getObjectContent(),
                Charsets.UTF_8))) {
            recordIdList = CharStreams.readLines(recordIdReader);
        }

        // for each record ID, get the upload ID
        List<String> uploadIdList = new ArrayList<>();
        for (String oneRecordId : recordIdList) {
            // rate limit so we down brown out DDB
            try {
                Thread.sleep(40);
            } catch (InterruptedException ex) {
                logger.error("Interrupted while sleeping: " + ex.getMessage(), ex);
            }

            HealthDataRecord record;
            try {
                record = healthDataDao.getRecordById(oneRecordId);
            } catch (RuntimeException ex) {
                // Ensure that errors won't fail the entire backfill. Log an error and move on.
                String errMsg = "Error getting record for ID " + oneRecordId + ": " + ex.getMessage();
                logger.error(errMsg, ex);
                recordMessage(task, callback, errMsg);
                continue;
            }
            if (record == null) {
                String errMsg = "No record for ID " + oneRecordId;
                logger.error(errMsg);
                recordMessage(task, callback, errMsg);
                continue;
            }

            uploadIdList.add(record.getUploadId());
        }

        return uploadIdList;
    }
}
