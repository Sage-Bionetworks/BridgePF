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
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
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
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    private UploadSessionCredentialsService uploadCredentailsService;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;
    private UploadDao uploadDao;
    private Validator validator;

    @Autowired
    public void setUploadSessionCredentialsService(UploadSessionCredentialsService uploadCredentialsService) {
        this.uploadCredentailsService = uploadCredentialsService;
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
    @Autowired
    public void setValidator(UploadValidator validator) {
        this.validator = validator;
    }

    public UploadSession createUpload(User user, UploadRequest uploadRequest) {
        Validate.entityThrowingException(validator, uploadRequest);

        // For all new uploads, the upload ID in DynamoDB is the same as the S3 Object ID
        Upload upload = uploadDao.createUpload(uploadRequest, user.getHealthCode());
        final String uploadId = upload.getUploadId();
        GeneratePresignedUrlRequest presignedUrlRequest =
                new GeneratePresignedUrlRequest(BUCKET, uploadId, HttpMethod.PUT);

        // Expiration
        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + EXPIRATION);
        presignedUrlRequest.setExpiration(expiration);

        // Temporary session credentials
        presignedUrlRequest.setRequestCredentials(uploadCredentailsService.getSessionCredentials());

        // Ask for server-side encryption
        presignedUrlRequest.addRequestParameter(SERVER_SIDE_ENCRYPTION, AES_256_SERVER_SIDE_ENCRYPTION);

        // Additional headers for signing
        presignedUrlRequest.setContentMd5(uploadRequest.getContentMd5());
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
     * @param user
     *         calling user, must be non-null
     * @param uploadId
     *         ID of upload to fetch, must be non-null and non-empty
     * @return upload metadata object
     */
    public Upload getUpload(@Nonnull User user, @Nonnull String uploadId) {
        if (Strings.isNullOrEmpty(uploadId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadId"));
        }
        Upload upload = uploadDao.getUpload(uploadId);

        // check health code
        if (!user.getHealthCode().equals(upload.getHealthCode())) {
            throw new UnauthorizedException();
        }

        return upload;
    }

    public void uploadComplete(Upload upload) {
        String uploadId = upload.getUploadId();

        // We don't want to kick off upload validation on an upload that already has upload validation.
        if (!upload.canBeValidated()) {
            logger.info(String.format("uploadComplete called for upload %s, which is already complete", uploadId));
            return;
        }

        final String objectId = upload.getObjectId();
        ObjectMetadata obj;
        try {
            obj = s3Client.getObjectMetadata(BUCKET, objectId);
        } catch (AmazonClientException ex) {
            throw new NotFoundException(ex);
        }
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            logger.error("Missing S3 server-side encryption (SSE) for presigned upload " + uploadId + ".");
        }
        uploadDao.uploadComplete(upload);
    }
}
