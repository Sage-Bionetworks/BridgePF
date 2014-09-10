package org.sagebionetworks.bridge.services;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

public class UploadServiceImpl implements UploadService {

    private static final long expire = 30 * 1000; // 30 seconds
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    private AmazonS3 s3UploadClient;

    public void setS3UploadClient(AmazonS3 s3UploadClient) {
        this.s3UploadClient = s3UploadClient;
    }

    @Override
    public URL createUpload() {

        final Date expiration = DateTime.now(DateTimeZone.UTC).toDate();
        expiration.setTime(expiration.getTime() + expire);

        String id = UUID.randomUUID().toString();
        GeneratePresignedUrlRequest presignedUrlRequest = 
                new GeneratePresignedUrlRequest(BUCKET, id);
        presignedUrlRequest.setMethod(HttpMethod.PUT); 
        presignedUrlRequest.setExpiration(expiration);
        // presignedUrlRequest.setContentMd5(md5);
        // presignedUrlRequest.setContentType(contentType);
        return s3UploadClient.generatePresignedUrl(presignedUrlRequest);
    }

    @Override
    public void uploadComplete(String id) {
    }
}
