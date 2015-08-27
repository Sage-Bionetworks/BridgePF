package org.sagebionetworks.bridge.services.backfill;

import java.io.IOException;
import java.util.List;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.UploadValidationService;

/**
 * Re-drives upload validation. Depending on the sub-class, this can get upload IDs from a variety of sources. See
 * {@link UploadValidationByUploadIdBackfill} and TODO
 */
public abstract class UploadValidationBackfill extends AsyncBackfillTemplate {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationBackfill.class);

    private HealthCodeDao healthCodeDao;
    private S3Helper s3Helper;
    private UploadDao uploadDao;
    private UploadValidationService uploadValidationService;

    /** DAO for getting the study ID from a health code. This is configured by Spring. */
    @Autowired
    public final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    /** @see #setS3Helper */
    protected S3Helper getS3Helper() {
        return s3Helper;
    }

    /** S3 helper for reading file of upload IDs. */
    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
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
        // get list of upload IDs
        List<String> uploadIdList;
        try {
            uploadIdList = getUploadIdList(task, callback);
        } catch (IOException ex) {
            // doBackfill() super class doesn't declare exceptions. Wrap this in a RuntimeException.
            throw new RuntimeException(ex);
        }

        for (String oneUploadId : uploadIdList) {
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

        logger.info("UploadValidationBackfill complete");
    }

    /** Subclasses should override this to return a list of upload IDs to redrive upload validation for. */
    protected abstract List<String> getUploadIdList(BackfillTask task, BackfillCallback callback) throws IOException;
}
