package org.sagebionetworks.bridge.s3;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.common.base.Charsets;
import org.junit.Test;

public class S3HelperTest {
    // Test strategy is that given a mock input stream from a mock S3 object, the S3Helper can still turn that
    // input stream into a byte array or a string.

    @Test
    public void readAsBytes() throws Exception {
        String bucket = "bucket-with-bytes";
        String key = "key-with-bytes";
        String content = "this is the answer in bytes";

        S3Helper testS3Helper = setupWithMockS3(bucket, key, content);
        byte[] retValBytes = testS3Helper.readS3FileAsBytes(bucket, key);
        assertEquals(content, new String(retValBytes, Charsets.UTF_8));
    }

    @Test
    public void readAsString() throws Exception {
        String bucket = "test-bucket";
        String key = "test-key";
        String content = "this is the answer";

        S3Helper testS3Helper = setupWithMockS3(bucket, key, content);
        String retVal = testS3Helper.readS3FileAsString(bucket, key);
        assertEquals(content, retVal);
    }

    private static S3Helper setupWithMockS3(String bucket, String key, String content) {
        // mock S3 stream
        byte[] contentBytes = content.getBytes(Charsets.UTF_8);
        InputStream contentStream = new ByteArrayInputStream(contentBytes);

        // not sure this is safe, but this is the easiest way to mock an S3 stream
        S3ObjectInputStream mockS3Stream = new S3ObjectInputStream(contentStream, null, false);

        // mock S3 object
        S3Object mockS3Object = new S3Object();
        mockS3Object.setObjectContent(mockS3Stream);

        // mock S3 client
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        when(mockS3Client.getObject(bucket, key)).thenReturn(mockS3Object);

        // set up test S3 helper
        S3Helper testS3Helper = new S3Helper();
        testS3Helper.setS3Client(mockS3Client);
        return testS3Helper;
    }
}
