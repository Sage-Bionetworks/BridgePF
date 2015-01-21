package org.sagebionetworks.bridge.s3;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Helper class that simplifies reading S3 files. */
@Component
public class S3Helper {
    private AmazonS3Client s3Client;

    /** S3 Client. This is configured by Spring. */
    @Autowired
    public void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Read the given S3 file contents as a string.
     *
     * @param bucket
     *         S3 bucket to read from, must be non-null and non-empty
     * @param key
     *         S3 key (filename), must be non-null and non-empty
     * @return the S3 file contents as a string
     * @throws IOException
     *         if closing the stream fails
     */
    public String readS3FileAsString(@Nonnull String bucket, @Nonnull String key) throws IOException {
        S3Object s3File = s3Client.getObject(bucket, key);
        try (InputStream s3InputStream = s3File.getObjectContent();
                InputStreamReader isReader = new InputStreamReader(s3InputStream, Charsets.UTF_8);
                BufferedReader s3Reader = new BufferedReader(isReader)) {
            return CharStreams.toString(s3Reader);
        }
    }
}
