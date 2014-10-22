package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;

import java.net.URL;
import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.UploadRequest;
import org.sagebionetworks.bridge.models.UploadSession;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Validator;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class UploadServiceImpl implements UploadService {

    private final Logger logger = LoggerFactory.getLogger(UploadServiceImpl.class);

    private static final long EXPIRATION = 60 * 1000; // 1 minute
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket.pd");

    private UploadSessionCredentialsService uploadCredentailsService;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;
    private UploadDao uploadDao;
    private Validator validator;

    public void setUploadSessionCredentialsService(UploadSessionCredentialsService uploadCredentialsService) {
        this.uploadCredentailsService = uploadCredentialsService;
    }
    public void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }
    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }
    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }
    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public UploadSession createUpload(User user, UploadRequest uploadRequest) {
        Validate.entityThrowingException(validator, uploadRequest);
        
        final String uploadId = uploadDao.createUpload(uploadRequest, user.getHealthDataCode());
        final String objectId = uploadDao.getObjectId(uploadId);
        GeneratePresignedUrlRequest presignedUrlRequest = 
                new GeneratePresignedUrlRequest(BUCKET, objectId, HttpMethod.PUT);

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

    @Override
    public void uploadComplete(final String uploadId) {
        final String objectId = uploadDao.getObjectId(uploadId);
        ObjectMetadata obj = s3Client.getObjectMetadata(BUCKET, objectId);
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            logger.error("Missing S3 server-side encryption (SSE) for presigned upload " + uploadId + ".");
        }
        uploadDao.uploadComplete(uploadId);
    }
}
