package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.validators.UploadValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    private static final long EXPIRATION = 24 * 60 * 60 * 1000; // 24 hours
    
    // package-scoped to be available in unit tests
    static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";
    static final String CONFIG_KEY_UPLOAD_DUPE_STUDY_WHITELIST = "upload.dupe.study.whitelist";

    private Set<String> dupeStudyWhitelist;
    private HealthDataService healthDataService;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;
    private String uploadBucket;
    private UploadDao uploadDao;
    private UploadSessionCredentialsService uploadCredentailsService;
    private UploadDedupeDao uploadDedupeDao;
    private UploadValidationService uploadValidationService;
    private Validator validator;

    // These parameters can be overriden to facilitate testing.
    // By default, we sleep 5 seconds, including right at the start and end. This means on our 7th iteration,
    // 30 seconds will have passed.
    private int pollValidationStatusMaxIterations = 7;
    private long pollValidationStatusSleepMillis = 5000;

    /** Sets parameters from the specified Bridge config. */
    @Autowired
    final void setConfig(BridgeConfig config) {
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);

        // This is the set of study IDs where we ignore dedupe logic. This configuration exists (1) for integration
        // tests, where we always upload the same files over and over and (2) for studies where it's valid and expected
        // for apps to always submit the same files over and over again.
        dupeStudyWhitelist = ImmutableSet.copyOf(config.getList(CONFIG_KEY_UPLOAD_DUPE_STUDY_WHITELIST));
    }

    /**
     * Health data record service. This is needed to fetch the health data record when constructing the upload
     * validation status.
     */
    @Autowired
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    @Resource(name = "s3UploadClient")
    public void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }
    @Resource(name = "s3Client")
    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }
    @Autowired
    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /** Upload dedupe DAO, for checking to see if an upload is a dupe. (And eventually dedupe if it is.) */
    @Autowired
    final void setUploadDedupeDao(UploadDedupeDao uploadDedupeDao) {
        this.uploadDedupeDao = uploadDedupeDao;
    }

    @Autowired
    public void setUploadSessionCredentialsService(UploadSessionCredentialsService uploadCredentialsService) {
        this.uploadCredentailsService = uploadCredentialsService;
    }

    /** Service handler for upload validation. This is configured by Spring. */
    @Autowired
    final void setUploadValidationService(UploadValidationService uploadValidationService) {
        this.uploadValidationService = uploadValidationService;
    }

    @Autowired
    public void setValidator(UploadValidator validator) {
        this.validator = validator;
    }

    /**
     * Number of iterations while polling for validation status before we time out. This is used primarily by tests to
     * reduce the amount of wait time during tests.
     */
    public final void setPollValidationStatusMaxIterations(int pollValidationStatusMaxIterations) {
        this.pollValidationStatusMaxIterations = pollValidationStatusMaxIterations;
    }

    /**
     * Milliseconds to sleep per iterations while polling for validation status. This is used primarily by tests to
     * reduce the amount of wait time during tests.
     */
    public final void setPollValidationStatusSleepMillis(long pollValidationStatusSleepMillis) {
        this.pollValidationStatusSleepMillis = pollValidationStatusSleepMillis;
    }

    public UploadSession createUpload(StudyIdentifier studyId, StudyParticipant participant, UploadRequest uploadRequest) {
        Validate.entityThrowingException(validator, uploadRequest);

        // Check to see if upload is a dupe, and if it is, get the upload status.
        String uploadMd5 = uploadRequest.getContentMd5();
        DateTime uploadRequestedOn = DateUtils.getCurrentDateTime();
        String originalUploadId = null;
        UploadStatus originalUploadStatus = null;

        // Dedupe logic gated on whitelist.
        if (!dupeStudyWhitelist.contains(studyId.getIdentifier())) {
            try {
                originalUploadId = uploadDedupeDao.getDuplicate(participant.getHealthCode(), uploadMd5,
                        uploadRequestedOn);
                if (originalUploadId != null) {
                    Upload originalUpload = uploadDao.getUpload(originalUploadId);
                    originalUploadStatus = originalUpload.getStatus();
                }
            } catch (RuntimeException ex) {
                // Don't want dedupe logic to fail the upload. Log an error and swallow the exception.
                logger.error("Error deduping upload: " + ex.getMessage(), ex);
            }
        }

        String uploadId;
        if (originalUploadId != null && originalUploadStatus == UploadStatus.REQUESTED) {
            // This is a dupe of a previous upload, and that previous upload is incomplete (REQUESTED). Instead of
            // creating a new upload in the upload table, reactivate the old one.
            uploadId = originalUploadId;
        } else {
            // This is a new upload.
            Upload upload = uploadDao.createUpload(uploadRequest, studyId, participant.getHealthCode(),
                    originalUploadId);
            uploadId = upload.getUploadId();

            if (originalUploadId != null) {
                // We had a dupe of a previous completed upload. Log this for future analysis.
                logger.info("Detected dupe: Study " + studyId.getIdentifier() + ", upload " + uploadId +
                        " is a dupe of " + originalUploadId);
            } else {
                try {
                    // Not a dupe. Register this dupe so we can detect dupes of this.
                    uploadDedupeDao.registerUpload(participant.getHealthCode(), uploadMd5, uploadRequestedOn, uploadId);
                } catch (RuntimeException ex) {
                    // Don't want dedupe logic to fail the upload. Log an error and swallow the exception.
                    logger.error("Error registering upload " + uploadId + " in dedupe table: " + ex.getMessage(), ex);
                }
            }
        }

        // Upload ID in DynamoDB is the same as the S3 Object ID
        GeneratePresignedUrlRequest presignedUrlRequest =
                new GeneratePresignedUrlRequest(uploadBucket, uploadId, HttpMethod.PUT);

        // Expiration
        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + EXPIRATION);
        presignedUrlRequest.setExpiration(expiration);

        // Temporary session credentials
        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(uploadCredentailsService.getSessionCredentials());
        presignedUrlRequest.setRequestCredentialsProvider(credentialsProvider);

        // Ask for server-side encryption
        presignedUrlRequest.addRequestParameter(SERVER_SIDE_ENCRYPTION, AES_256_SERVER_SIDE_ENCRYPTION);

        // Additional headers for signing
        presignedUrlRequest.setContentMd5(uploadMd5);
        presignedUrlRequest.setContentType(uploadRequest.getContentType());

        URL url = s3UploadClient.generatePresignedUrl(presignedUrlRequest);
        return new UploadSession(uploadId, url, expiration.getTime());
    }

    /**
     * <p>
     * Get upload service handler. This isn't currently exposed directly to the users, but is currently used by the
     * controller class to call both uploadComplete() and upload validation APIs.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload metadata object
     */
    public Upload getUpload(@Nonnull String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadId"));
        }
        return uploadDao.getUpload(uploadId);
    }
    
    public UploadView getUploadView(String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadId"));
        }
        Upload upload = uploadDao.getUpload(uploadId);
        return uploadToUploadView(upload, true);
    }

    /**
     * <p>Get uploads for a given user in a time window. Start and end time are optional. If neither are provided, they 
     * default to the last day of uploads. If end time is not provided, the query ends at the time of the request. If the 
     * start time is not provided, it defaults to a day before the end time. The time window is constrained to two days 
     * of uploads (though those days can be any period in time). </p>
     */
    public ForwardCursorPagedResourceList<UploadView> getUploads(@Nonnull String healthCode,
            @Nullable DateTime startTime, @Nullable DateTime endTime, Integer pageSize, @Nullable String offsetKey) {
        checkNotNull(healthCode);
        
        return getUploads(startTime, endTime, (start, end)-> {
            return uploadDao.getUploads(healthCode, start, end,
                    (pageSize == null ? API_DEFAULT_PAGE_SIZE : pageSize.intValue()), offsetKey);
        });
    }
    
    /**
     * <p>Get uploads for an entire study in a time window. Start and end time are optional. If neither are provided, they 
     * default to the last day of uploads. If end time is not provided, the query ends at the time of the request. If the 
     * start time is not provided, it defaults to a day before the end time. The time window is constrained to two days 
     * of uploads (though those days can be any period in time). </p>
     */
    public ForwardCursorPagedResourceList<UploadView> getStudyUploads(@Nonnull StudyIdentifier studyId,
            @Nullable DateTime startTime, @Nullable DateTime endTime, @Nullable Integer pageSize, @Nullable String offsetKey) {
        checkNotNull(studyId);

        // in case clients didn't set page size up
        return getUploads(startTime, endTime, (start, end)-> 
            uploadDao.getStudyUploads(studyId, start, end,
                    (pageSize == null ? API_DEFAULT_PAGE_SIZE : pageSize.intValue()), offsetKey)
        );
    }
    
    private ForwardCursorPagedResourceList<UploadView> getUploads(DateTime startTime, DateTime endTime, UploadSupplier supplier) {
        checkNotNull(supplier);
        
        if (startTime == null && endTime == null) {
            endTime = DateTime.now();
            startTime = endTime.minusDays(1);
        } else if (endTime == null) {
            endTime = startTime.plusDays(1);
        } else if (startTime == null) {
            startTime = endTime.minusDays(1);
        }
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("Start time cannot be after end time: " + startTime + "-" + endTime);
        }
        
        ForwardCursorPagedResourceList<Upload> list = supplier.get(startTime, endTime);

        // This summary view is accessible to developers, so we do not include details of the health data record.
        List<UploadView> views = list.getItems().stream()
                .map(upload -> uploadToUploadView(upload, false))
                .collect(Collectors.toList());
        
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(views, list.getNextPageOffsetKey());
        for (Map.Entry<String,Object> entry : list.getRequestParams().entrySet()) {
            page.withRequestParam(entry.getKey(), entry.getValue());
        }
        return page;
    }
    
    private UploadView uploadToUploadView(Upload upload, boolean includeHealthDataRecord) {
        UploadView.Builder builder = new UploadView.Builder();
        builder.withUpload(upload);
        if (upload.getRecordId() != null) {
            HealthDataRecord record = healthDataService.getRecordById(upload.getRecordId());
            if (record != null) {
                if (includeHealthDataRecord) {
                    builder.withHealthDataRecord(record);
                } else {
                    builder.withSchemaId(record.getSchemaId());
                    builder.withSchemaRevision(record.getSchemaRevision());
                    builder.withHealthRecordExporterStatus(record.getSynapseExporterStatus());
                }
            }
        }
        return builder.build();
    }

    /**
     * <p>
     * Gets validation status and messages for the given upload ID. This includes the health data record, if one was
     * created for the upload.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload validation status, which includes the health data record if one was created
     */
    public UploadValidationStatus getUploadValidationStatus(@Nonnull String uploadId) {
        Upload upload = getUpload(uploadId);

        // get record, if it exists
        HealthDataRecord record = null;
        String recordId = upload.getRecordId();
        if (!Strings.isNullOrEmpty(recordId)) {
            try {
                record = healthDataService.getRecordById(recordId);
            } catch (RuntimeException ex) {
                // Underlying service failed to get the health data record. Log a warning, but move on.
                logger.warn("Error getting record ID " + recordId + " for upload ID " + uploadId + ": "
                        + ex.getMessage(), ex);
            }
        }

        UploadValidationStatus validationStatus = UploadValidationStatus.from(upload, record);
        return validationStatus;
    }

    /**
     * Polls for validation status for a given upload ID. Polls until validation is complete or otherwise is in a state
     * where further polling won't get any results (like validation failed, or upload is requested but not yet
     * uploaded), or until it times out. See getUploadValidationStatus() for more details.
     */
    public UploadValidationStatus pollUploadValidationStatusUntilComplete(String uploadId) {
        // Loop logic is a little wonky. (Loop-and-a-half problem.) Use an infinite loop here and rely on tests to make
        // sure we don't go infinite.
        int numIters = 0;
        while (true) {
            UploadValidationStatus validationStatus = getUploadValidationStatus(uploadId);
            if (validationStatus.getStatus() != UploadStatus.VALIDATION_IN_PROGRESS) {
                // Validation is either finished processing, or otherwise in a state where it's pointless to wait.
                // Return the answer we have now.
                return validationStatus;
            }

            // Short-circuit: If we've elapsed our timeout, just exit now. Don't wait for a sleep.
            numIters++;
            if (numIters >= pollValidationStatusMaxIterations) {
                throw new BridgeServiceException("Timeout polling validation status for upload " + uploadId);
            }

            // Sleep and try again.
            try {
                Thread.sleep(pollValidationStatusSleepMillis);
            } catch (InterruptedException ex) {
                logger.error("Interrupted while polling for validation status: " + ex.getMessage());
            }
        }
    }

    public void uploadComplete(StudyIdentifier studyId, UploadCompletionClient completedBy, Upload upload,
            boolean redrive) {
        String uploadId = upload.getUploadId();

        // We don't want to kick off upload validation on an upload that already has upload validation.
        if (!upload.canBeValidated() && !redrive) {
            logger.info(String.format("uploadComplete called for upload %s, which is already complete", uploadId));
            return;
        }

        final String objectId = upload.getObjectId();
        ObjectMetadata obj;
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            obj = s3Client.getObjectMetadata(uploadBucket, objectId);
            logger.info("Finished getting S3 metadata for bucket " + uploadBucket + " key " + objectId + " in " +
                    stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() == 404) {
                throw new NotFoundException(ex);
            } else {
                // Only S3 404s are mapped to 404s. Everything else is an internal server error.
                throw new BridgeServiceException(ex);
            }
        }
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            logger.error("Missing S3 server-side encryption (SSE) for presigned upload " + uploadId + ".");
        }

        try {
            uploadDao.uploadComplete(completedBy, upload);
        } catch (ConcurrentModificationException ex) {
            // The old workflow is the app calls uploadComplete. The new workflow has an S3 trigger to call
            // uploadComplete. During the transition, it's very likely that this will be called twice, sometimes
            // concurrently. As such, we should log and squelch the ConcurrentModificationException.
            logger.info("Concurrent modification of upload " + uploadId + " while marking upload complete");

            // Also short-circuit the call early, so we don't end up validating the upload twice, as this causes errors
            // and duplicate records.
            return;
        }

        // kick off upload validation
        uploadValidationService.validateUpload(studyId, upload);
    }
    
    public void deleteUploadsForHealthCode(String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        uploadDao.deleteUploadsForHealthCode(healthCode);
    }

    @FunctionalInterface
    private static interface UploadSupplier {
        ForwardCursorPagedResourceList<Upload> get(DateTime startTime, DateTime endTime);
    }
}
