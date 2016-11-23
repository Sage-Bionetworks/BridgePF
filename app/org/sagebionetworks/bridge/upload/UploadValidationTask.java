package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

/**
 * This class represents an asynchronous upload validation task, corresponding with exactly one upload. It implements
 * the Runnable interface, so we can run it as asynchronous code.
 */
public class UploadValidationTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationTask.class);

    private final UploadValidationContext context;

    private List<UploadValidationHandler> handlerList;
    private UploadDao uploadDao;
    private HealthDataService healthDataService;

    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public HealthDataService getHealthDataService() {
        return this.healthDataService;
    }

    /**
     * Constructs an upload validation task instance with the given context. This should only be called by the
     * factory, or by unit tests.
     * @param context context for this specific task, must be non-null
     */
    /* package-scoped */ UploadValidationTask(@Nonnull UploadValidationContext context) {
        this.context = context;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped */ UploadValidationContext getContext() {
        return context;
    }

    /** List of validation handlers. This is configured by Spring through the task factory. */
    public void setHandlerList(List<UploadValidationHandler> handlerList) {
        this.handlerList = handlerList;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped*/ List<UploadValidationHandler> getHandlerList() {
        return handlerList;
    }

    /** Upload DAO, for writing upload validation status. This is configured by Spring through the task factory. */
    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped*/ UploadDao getUploadDao() {
        return uploadDao;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        Stopwatch stopwatch = Stopwatch.createUnstarted();
        for (UploadValidationHandler oneHandler : handlerList) {
            String handlerName = oneHandler.getClass().getName();
            stopwatch.start();

            try {
                oneHandler.handle(context);
            } catch (Throwable ex) {
                context.setSuccess(false);
                context.addMessage(String.format("Exception thrown from upload validation handler %s: %s: %s",
                        handlerName, ex.getClass().getName(), ex.getMessage()));

                if (ex instanceof Error) {
                    // Something really bad happened, like an OutOfMemoryError. Log this at the error level.
                    logger.error(String.format("Critical error in upload validation handler %s for study %s, " +
                            "upload %s, filename %s: %s: %s", handlerName, context.getStudy().getIdentifier(),
                            context.getUpload().getUploadId(), context.getUpload().getFilename(),
                            ex.getClass().getName(), ex.getMessage()), ex);
                } else {
                    // Upload validation failed. Since there are a lot of garbage uploads, log this at the info level
                    // so it doesn't set off our alarms. Once the garbage uploads are cleaned up, we can bump this back
                    // up to warning.
                    logger.info(String.format("Exception thrown from upload validation handler %s for study %s, " +
                            "upload %s, filename %s: %s: %s", handlerName, context.getStudy().getIdentifier(),
                            context.getUpload().getUploadId(), context.getUpload().getFilename(),
                            ex.getClass().getName(), ex.getMessage()), ex);
                }
                break;
            } finally {
                long elapsedMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                stopwatch.reset();
                // TODO: send this to somewhere other than the logs
                logger.info(String.format("Upload validation handler %s took %d ms", handlerName, elapsedMillis));
            }
        }

        // write validation status to the upload DAO
        UploadStatus status = context.getSuccess() ? UploadStatus.SUCCEEDED : UploadStatus.VALIDATION_FAILED;
        try {
            uploadDao.writeValidationStatus(context.getUpload(), status, context.getMessageList(),
                    context.getRecordId());
            logger.info(String.format("Upload validation for study %s, upload %s, record %s, with status %s",
                    context.getStudy().getIdentifier(), context.getUpload().getUploadId(), context.getRecordId(),
                    status));
        } catch (RuntimeException ex) {
            // ExceptionInterceptor doesn't handle asynchronous tasks, so we'll need to catch exceptions and log them
            // manually. Use the log helper function so we can verify it in unit tests.
            logWriteValidationStatusException(status, ex);
        }

        // TODO: if validation fails, wipe the files from S3

        // dedupe logic over here:
        try {
            dedupeHelper(context.getRecordId());
        } catch (Throwable e) {
            logErrorMsg(e);
        }

    }

    // helper method to log exception
    void logErrorMsg(Throwable e) {
        logger.error("An error occurred:", e);
    }

    /**
     * helper method: query healthdatarecord by healthcode, schema and createdOn from the upload just finished above,
     * if there are more than 1 such records in ddb, there are duplicates. log that information but not stop or delete anything right now
     * @param healthRecordId
     */
    private void dedupeHelper(String healthRecordId) throws BadRequestException {
        if (healthRecordId == null) {
            return; // no record yet, no need to check duplicates
        }
        // get necessary information for query first
        HealthDataRecord record = healthDataService.getRecordById(healthRecordId);

        // if there is no record in ddb, we should just finish dedupe logic since no record is a common case
        if (record == null) {
            return;
        }

        Long createdOn = record.getCreatedOn();
        String healthCode = record.getHealthCode();
        String schemaId = record.getSchemaId();

        List<HealthDataRecord> retList = healthDataService.getRecordsByHealthcodeCreatedOnSchemaId(healthCode, createdOn, schemaId);

        if (retList.size() > 1) {
            logger.info(String.format("Duplicate health data records for record id: %s, created on:  %s, schema id: %s, duplicate size: %s",
                    healthRecordId, createdOn, schemaId, retList.size()));

            logDuplicateUploadRecords(record, retList);
        }
        // else there is only one such record exists in ddb or no record exists yet, means no duplicate -- do nothing
    }

    void logDuplicateUploadRecords(HealthDataRecord originRecord, List<HealthDataRecord> dupeRecords) {
        logger.info("Origin Record: " + "record id: " + originRecord.getId());

        for (HealthDataRecord dupeRecord: dupeRecords) {
            if (!dupeRecord.getId().equals(originRecord.getId())) {
                logger.info("Duplicate HealthDataRecord: " + "record id: " + dupeRecord.getId()
                        + ", createdOn: " + dupeRecord.getCreatedOn().toString()
                        + ", schemaId: " + dupeRecord.getSchemaId()
                        + ", studyId: " + dupeRecord.getStudyId() + ", uploadDate: " + dupeRecord.getUploadDate().toString()
                        + ", uploadId: " + dupeRecord.getUploadId());
            }
        }
    }

    // Log helper. Unit tests will mock (spy) this, so we verify that we're catching and logging the exception.
    // Package-scoped so unit tests have access to this.
    void logWriteValidationStatusException(UploadStatus status, Exception ex) {
        logger.error("Exception writing validation status for study " + context.getStudy().getIdentifier() +
                ", upload " + context.getUpload().getUploadId() + ", record " + context.getRecordId() + ", status " +
                status + ": " + ex.getMessage(), ex);
    }
}
