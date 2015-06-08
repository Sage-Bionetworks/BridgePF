package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

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
import org.sagebionetworks.bridge.services.UploadValidationService;

/**
 * Backfills unvalidated uploads, either ones that failed (VALIDATION_FAILED) or ones that crashed partway through
 * (VALIDATION_IN_PROGRESS) between the specified dates. This is used for things like redriving large PD activities
 * that failed due to OutOfMemoryErrors.
 */
@Component("uploadValidationBackfill")
public class UploadValidationBackfill extends AsyncBackfillTemplate {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationBackfill.class);

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
        // Backfill could take a very long time. Set backfill timeout to 8 hrs.
        return 8 * 60 * 60;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        // Backfill should go from 2015-04-21 (when we saw a drop in asthma activities) to 2015-06-05 (when the
        // memory fix hit prod).
        String startDate = "2015-04-21";
        String endDate = "2015-06-05";

        recordMessage(task, callback, "Starting upload validation backfill from " + startDate + " to " + endDate);

        // query and iterate over uploads
        List<? extends Upload> uploadList = uploadDao.getFailedUploadsForDates(startDate, endDate);
        for (Upload oneUpload : uploadList) {
            // rate limit so we down starve threads or brown out DDB
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                logger.error("Interrupted while sleeping: " + ex.getMessage(), ex);
            }

            String uploadId = oneUpload.getUploadId();
            try {
                // Call uploadComplete() to reset the upload status and uploadDate. This will make the upload
                // eligible for validation again.
                uploadDao.uploadComplete(oneUpload);

                // Get study ID from health code. Upload validation needs this.
                String studyId = healthCodeDao.getStudyIdentifier(oneUpload.getHealthCode());
                StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);

                // Kick off upload validation.
                uploadValidationService.validateUpload(studyIdentifier, oneUpload);

                recordMessage(task, callback, "Backfilled upload ID " + uploadId);
            } catch (RuntimeException ex) {
                // Ensure that errors won't fail the entire backfill. Log an error and move on.
                String errMsg = "Error backfilling upload ID " + uploadId + ": " + ex.getMessage();
                logger.error(errMsg, ex);
                recordMessage(task, callback, errMsg);
            }
        }
    }
}
