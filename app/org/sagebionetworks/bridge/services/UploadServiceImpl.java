package org.sagebionetworks.bridge.services;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;

public class UploadServiceImpl implements UploadService {

    private static final Integer TOKEN_EXPIRE = Integer.valueOf(900); // 15 minutes
    private static final long UPLOAD_EXPIRE = 60 * 1000; // 1 minute
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    private AWSSecurityTokenServiceClient tokenServiceClient;
    private AmazonS3 s3UploadClient;

    public void setTokenServiceClient(AWSSecurityTokenServiceClient tokenServiceClient) {
        this.tokenServiceClient = tokenServiceClient;
    }
    public void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }

    @Override
    public URL createUpload() {

        // Create temporary credentials
        GetSessionTokenRequest getSessionTokenRequest = new GetSessionTokenRequest();
        getSessionTokenRequest.setDurationSeconds(TOKEN_EXPIRE);
        GetSessionTokenResult sessionTokenResult = tokenServiceClient.getSessionToken(getSessionTokenRequest);
        Credentials sessionCredentials = sessionTokenResult.getCredentials();
        final AWSCredentials credentials = new BasicSessionCredentials(
                sessionCredentials.getAccessKeyId(),
                sessionCredentials.getSecretAccessKey(),
                sessionCredentials.getSessionToken());

        // Compute upload expiration
        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + UPLOAD_EXPIRE);

        String id = UUID.randomUUID().toString();
        GeneratePresignedUrlRequest presignedUrlRequest = 
                new GeneratePresignedUrlRequest(BUCKET, id);
        presignedUrlRequest.setMethod(HttpMethod.PUT); 
        presignedUrlRequest.setExpiration(expiration);
        presignedUrlRequest.setRequestCredentials(credentials);
        presignedUrlRequest.setContentMd5("cc9982c0879594647c3fd05eb9534097");
        presignedUrlRequest.setContentType("text/plain");
        return s3UploadClient.generatePresignedUrl(presignedUrlRequest);
    }

    @Override
    public void uploadComplete(String id) {
    }
}
