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
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.UploadValidationService;

/**
 * <p>
 * Re-drives backfills for a list of upload IDs. These upload IDs live in a file called
 * upload-validation-backfill-uploadIds in an S3 bucket called org-sagebridge-backfill-prod (replace prod) with your
 * env name.
 * </p>
 * <p>
 * File format is one upload ID per line.
 * </p>
 */
@Component("uploadValidationBackfill")
public class UploadValidationBackfill extends AsyncBackfillTemplate {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationBackfill.class);

    private static final String UPLOAD_ID_BUCKET = "org-sagebridge-backfill-" + BridgeConfigFactory.getConfig()
            .getEnvironment().name().toLowerCase();
    private static final String UPLOAD_ID_FILENAME = "upload-validation-backfill-uploadIds";

    private HealthCodeDao healthCodeDao;
    private AmazonS3Client s3Client;
    private UploadDao uploadDao;
    private UploadValidationService uploadValidationService;

    /** DAO for getting the study ID from a health code. This is configured by Spring. */
    @Autowired
    public final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    /** S3 client for reading file of upload IDs. This is configured by Spring. */
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
        // We do roughly one upload per second. One hour should be enough for most cases.
        return 3600;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        // get file of list of upload IDs
        S3Object uploadIdFile = s3Client.getObject(UPLOAD_ID_BUCKET, UPLOAD_ID_FILENAME);
        try (BufferedReader uploadIdReader = new BufferedReader(new InputStreamReader(uploadIdFile.getObjectContent(),
                Charsets.UTF_8))) {
            // query and iterate over upload IDs
            String oneUploadId;
            while ((oneUploadId = uploadIdReader.readLine()) != null) {
                // rate limit so we down starve threads or brown out DDB
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted while sleeping: " + ex.getMessage(), ex);
                }

                try {
                    // Get upload.
                    Upload oneUpload = uploadDao.getUpload(oneUploadId);

                    // Get study ID from health code. Upload validation needs this.
                    String studyId = healthCodeDao.getStudyIdentifier(oneUpload.getHealthCode());
                    StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);

                    // Kick off upload validation.
                    uploadValidationService.validateUpload(studyIdentifier, oneUpload);

                    recordMessage(task, callback, "Backfilled upload ID " + oneUploadId);
                    logger.info("Backfilled upload ID " + oneUploadId);
                } catch (RuntimeException ex) {
                    // Ensure that errors won't fail the entire backfill. Log an error and move on.
                    String errMsg = "Error backfilling upload ID " + oneUploadId + ": " + ex.getMessage();
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
