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
    @Test
    public void test() throws Exception {
        // Test strategy is that given a mock input stream from a mock S3 object, the S3Helper can still turn that
        // input stream into a string.

        // mock S3 stream
        String answer = "This is the answer.";
        byte[] answerBytes = answer.getBytes(Charsets.UTF_8);
        InputStream answerStream = new ByteArrayInputStream(answerBytes);

        // not sure this is safe, but this is the easiest way to mock an S3 stream
        S3ObjectInputStream mockS3Stream = new S3ObjectInputStream(answerStream, null, false);

        // mock S3 object
        S3Object mockS3Object = new S3Object();
        mockS3Object.setObjectContent(mockS3Stream);

        // mock S3 client
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        when(mockS3Client.getObject("test-bucket", "test-key")).thenReturn(mockS3Object);

        // set up test S3 helper
        S3Helper testS3Helper = new S3Helper();
        testS3Helper.setS3Client(mockS3Client);

        // execute and validate
        String retVal = testS3Helper.readS3FileAsString("test-bucket", "test-key");
        assertEquals(answer, retVal);
    }
}
