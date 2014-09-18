package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class UploadServiceImpl implements UploadService {

    private static final long EXPIRATION = 60000 * 1000; // 1 minute
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket.pd");

    private UploadSessionCredentialsService uploadCredentailsService;
    private AmazonS3 s3UploadClient;
    private AmazonS3 s3Client;

    public void setUploadSessionCredentialsService(UploadSessionCredentialsService uploadCredentialsService) {
        this.uploadCredentailsService = uploadCredentialsService;
    }
    public void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }
    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public URL createUpload() {
        final AWSCredentials credentials = uploadCredentailsService.getSessionCredentials();
        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + EXPIRATION);
        GeneratePresignedUrlRequest presignedUrlRequest = 
                new GeneratePresignedUrlRequest(BUCKET, UUID.randomUUID().toString());
        presignedUrlRequest.setMethod(HttpMethod.PUT); 
        presignedUrlRequest.setExpiration(expiration);
        presignedUrlRequest.setRequestCredentials(credentials);
        presignedUrlRequest.addRequestParameter(SERVER_SIDE_ENCRYPTION, AES_256_SERVER_SIDE_ENCRYPTION);
        return s3UploadClient.generatePresignedUrl(presignedUrlRequest);
    }

    @Override
    public void uploadComplete(String key) {
        ObjectMetadata obj = s3Client.getObjectMetadata(BUCKET, key);
        String sse = obj.getSSEAlgorithm();
        if (!AES_256_SERVER_SIDE_ENCRYPTION.equals(sse)) {
            throw new RuntimeException("Server-side encryption failure.");
        }
    }
}
