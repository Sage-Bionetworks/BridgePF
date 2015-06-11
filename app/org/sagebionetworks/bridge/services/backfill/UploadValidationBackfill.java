package org.sagebionetworks.bridge.services.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.services.UploadValidationService;

/**
 * Backfills unvalidated uploads from a list of upload IDs. Only accept uploads that failed (VALIDATION_FAILED) or ones
 * that crashed partway through (VALIDATION_IN_PROGRESS). This is used for things like redriving large PD activities
 * that failed due to OutOfMemoryErrors.
 */
@Component("uploadValidationBackfill")
public class UploadValidationBackfill extends AsyncBackfillTemplate {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationBackfill.class);

    private static final String[] uploadIdArray = {
            "97ae3ff8-00ef-4512-86fb-3df612618926",
            "5506da3a-33e5-4f37-adb5-bd775b14fdb7",
            "6331f331-5f1c-48c9-b509-5d874c002598",
            "4642289b-7da7-4cb3-8833-2a84ef4b589f",
            "e2f37830-3433-4364-ae13-94b63eadec30",
            "577faaf2-8932-476c-a26a-cdf526855a52",
            "80dab53d-9757-4bf0-8858-ff2219e45a0d",
            "2db4d335-6b47-4700-b57c-e1caeb7b2ead",
            "32228d19-a05e-4721-bf05-56fec361b002",
            "b91ff9a2-57dd-4ffa-9f25-2944deb12469",
            "d6035f81-7296-4e16-9415-e71f99912636",
            "0d64afe4-1606-411f-a1b7-5d4c5e9c164a",
            "6793a8d2-94ff-4661-80c5-a5c1f23c7f67",
            "a414289d-80c2-4d15-975e-c6cb13816fe5",
            "8ce4afd3-5fb5-46af-bbaf-fb08640cc173"
    };

    private HealthCodeDao healthCodeDao;
    private UploadDao uploadDao;
    private UploadValidationService uploadValidationService;

    /** DAO for getting the study ID from a health code. This is configured by Spring. */
    @Autowired
    public final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
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
        // 5 minutes should be more than enough time to backfill 15 records.
        return 5 * 60;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        // query and iterate over uploads
        for (String uploadId : uploadIdArray) {
            // rate limit so we down starve threads or brown out DDB
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                logger.error("Interrupted while sleeping: " + ex.getMessage(), ex);
            }

            try {
                // If it's already succeeded, don't backfill it
                Upload oneUpload = uploadDao.getUpload(uploadId);
                if (oneUpload.getStatus() == UploadStatus.SUCCEEDED) {
                    logger.info("Upload " + uploadId + " already succeeded, skipping backfill");
                    continue;
                }

                // Call uploadComplete() to reset the upload status and uploadDate. This will make the upload
                // eligible for validation again.
                uploadDao.uploadComplete(oneUpload);

                // Get study ID from health code. Upload validation needs this.
                String studyId = healthCodeDao.getStudyIdentifier(oneUpload.getHealthCode());
                StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);

                // Kick off upload validation.
                uploadValidationService.validateUpload(studyIdentifier, oneUpload);

                recordMessage(task, callback, "Backfilled upload ID " + uploadId);
                logger.info("Backfilled upload ID " + uploadId);
            } catch (RuntimeException ex) {
                // Ensure that errors won't fail the entire backfill. Log an error and move on.
                String errMsg = "Error backfilling upload ID " + uploadId + ": " + ex.getMessage();
                logger.error(errMsg, ex);
                recordMessage(task, callback, errMsg);
            }
        }

        logger.info("UploadValidationBackfill complete");
    }
}
