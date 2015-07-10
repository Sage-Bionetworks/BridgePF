package org.sagebionetworks.bridge.services.backfill;

import javax.annotation.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.UploadValidationService;

/**
 * <p>
 * Re-drives backfills for a list of record IDs. These record IDs live in a file called
 * upload-validation-backfill-recordIds in an S3 bucket called org-sagebridge-backfill-prod (replace prod) with your
 * env name.
 * </p>
 * <p>
 * File format is one record ID per line.
 * </p>
 */
@Component("uploadValidationBackfill")
public class UploadValidationBackfill extends AsyncBackfillTemplate {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationBackfill.class);

    private static final String RECORD_ID_BUCKET = "org-sagebridge-backfill-" + BridgeConfigFactory.getConfig()
            .getEnvironment().name().toLowerCase();
    private static final String RECORD_ID_FILENAME = "upload-validation-backfill-recordIds";

    private HealthCodeDao healthCodeDao;
    private HealthDataDao healthDataDao;
    private AmazonS3Client s3Client;
    private UploadDao uploadDao;
    private UploadValidationService uploadValidationService;

    /** DAO for getting the study ID from a health code. This is configured by Spring. */
    @Autowired
    public final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    /** Health data DAO, used to get the upload ID from the record ID. This is configured by Spring. */
    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /** S3 client for reading file of record IDs. This is configured by Spring. */
    @Resource(name = "s3Client")
    public void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }

    /** DAO for manipulating upload objects. This is configured by Spring. */
    @Autowired
    public final void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /** Service handler for upload validation. This is configured by Spring. */
    @Autowired
    public final void setUploadValidationService(UploadValidationService uploadValidationService) {
        this.uploadValidationService = uploadValidationService;
    }

    @Override
    int getLockExpireInSeconds() {
        // We do roughly one record per second. One hour should be enough for most cases.
        return 3600;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        // get file of list of record IDs
        S3Object recordIdFile = s3Client.getObject(RECORD_ID_BUCKET, RECORD_ID_FILENAME);
        try (BufferedReader recordIdReader = new BufferedReader(new InputStreamReader(recordIdFile.getObjectContent(),
                Charsets.UTF_8))) {
            // query and iterate over record IDs
            String oneRecordId;
            while ((oneRecordId = recordIdReader.readLine()) != null) {
                // rate limit so we down starve threads or brown out DDB
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted while sleeping: " + ex.getMessage(), ex);
                }

                try {
                    // get upload ID for record ID
                    HealthDataRecord record = healthDataDao.getRecordById(oneRecordId);
                    String uploadId = record.getUploadId();

                    // Get upload.
                    Upload oneUpload = uploadDao.getUpload(uploadId);

                    // Get study ID from health code. Upload validation needs this.
                    String studyId = healthCodeDao.getStudyIdentifier(oneUpload.getHealthCode());
                    StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);

                    // Kick off upload validation.
                    uploadValidationService.validateUpload(studyIdentifier, oneUpload);

                    recordMessage(task, callback, "Backfilled record ID " + oneRecordId);
                    logger.info("Backfilled record ID " + oneRecordId);
                } catch (RuntimeException ex) {
                    // Ensure that errors won't fail the entire backfill. Log an error and move on.
                    String errMsg = "Error backfilling record ID " + oneRecordId + ": " + ex.getMessage();
                    logger.error(errMsg, ex);
                    recordMessage(task, callback, errMsg);
                }
            }
        } catch (IOException ex) {
            // reading from S3 failed
            // doBackfill() super class doesn't declare exceptions. Wrap this in a RuntimeException.
            throw new RuntimeException(ex);
        }

        logger.info("UploadValidationBackfill complete");
    }
}
