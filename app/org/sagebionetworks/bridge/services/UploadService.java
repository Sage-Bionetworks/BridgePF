package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import java.net.URL;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.google.common.base.Strings;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.validators.UploadValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

@Component
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    private static final long EXPIRATION = 60 * 1000; // 1 minute

    // package-scoped to be available in unit tests
    static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";

    private HealthDataService healthDataService;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;
    private String uploadBucket;
    private UploadDao uploadDao;
    private UploadSessionCredentialsService uploadCredentailsService;
    private UploadDedupeDao uploadDedupeDao;
    private UploadValidationService uploadValidationService;
    private Validator validator;

    /** Sets parameters from the specified Bridge config. */
    @Autowired
    final void setConfig(BridgeConfig config) {
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);
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

    public UploadSession createUpload(StudyIdentifier studyId, StudyParticipant participant, UploadRequest uploadRequest) {
        Validate.entityThrowingException(validator, uploadRequest);

        // Check to see if upload is a dupe, and if it is, get the upload status.
        String uploadMd5 = uploadRequest.getContentMd5();
        DateTime uploadRequestedOn = DateUtils.getCurrentDateTime();
        String originalUploadId = null;
        UploadStatus originalUploadStatus = null;
        try {
            originalUploadId = uploadDedupeDao.getDuplicate(participant.getHealthCode(), uploadMd5, uploadRequestedOn);
            if (originalUploadId != null) {
                Upload originalUpload = uploadDao.getUpload(originalUploadId);
                originalUploadStatus = originalUpload.getStatus();
            }
        } catch (RuntimeException ex) {
            // Don't want dedupe logic to fail the upload. Log an error and swallow the exception.
            logger.error("Error deduping upload: " + ex.getMessage(), ex);
        }

        String uploadId;
        if (originalUploadId != null && originalUploadStatus == UploadStatus.REQUESTED) {
            // This is a dupe of a previous upload, and that previous upload is incomplete (REQUESTED). Instead of
            // creating a new upload in the upload table, reactivate the old one.
            uploadId = originalUploadId;
        } else {
            // This is a new upload.
            Upload upload = uploadDao.createUpload(uploadRequest, participant.getHealthCode());
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
        presignedUrlRequest.setRequestCredentials(uploadCredentailsService.getSessionCredentials());

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
     * This method also validates to ensure that only the user who created the upload can access this upload.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param participant
     *         calling user, must be non-null
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload metadata object
     */
    public Upload getUpload(@Nonnull StudyParticipant participant, @Nonnull String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadId"));
        }
        Upload upload = uploadDao.getUpload(uploadId);

        // check health code
        if (!participant.getHealthCode().equals(upload.getHealthCode())) {
            throw new UnauthorizedException();
        }

        return upload;
    }

    /**
     * <p>
     * Gets validation status and messages for the given upload ID. This includes the health data record, if one was
     * created for the upload. The logged in user is passed in to verify that the logged in user owns the given upload.
     * </p>
     * <p>
     * user comes from the controller, and is guaranteed to be present. However, uploadId is user input and must be
     * validated.
     * </p>
     *
     * @param participant
     *         calling user, must be non-null
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload validation status, which includes the health data record if one was created
     */
    public UploadValidationStatus getUploadValidationStatus(@Nonnull StudyParticipant participant, @Nonnull String uploadId) {
        // The call to getUpload() also validates inputs and verifies the user matches.
        Upload upload = getUpload(participant, uploadId);

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

    public void uploadComplete(StudyIdentifier studyId, Upload upload) {
        String uploadId = upload.getUploadId();

        // We don't want to kick off upload validation on an upload that already has upload validation.
        if (!upload.canBeValidated()) {
            logger.info(String.format("uploadComplete called for upload %s, which is already complete", uploadId));
            return;
        }

        final String objectId = upload.getObjectId();
        ObjectMetadata obj;
        try {
            obj = s3Client.getObjectMetadata(uploadBucket, objectId);
        } catch (AmazonClientException ex) {
            throw new NotFoundException(ex);
        }
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            logger.error("Missing S3 server-side encryption (SSE) for presigned upload " + uploadId + ".");
        }
        uploadDao.uploadComplete(upload);

        // kick off upload validation
        uploadValidationService.validateUpload(studyId, upload);
    }
}
