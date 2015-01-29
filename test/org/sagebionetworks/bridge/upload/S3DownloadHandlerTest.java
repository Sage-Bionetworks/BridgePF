package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.google.common.base.Charsets;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.s3.S3Helper;

@SuppressWarnings("unchecked")
public class S3DownloadHandlerTest {
    @Test
    public void test() throws Exception {
        // The handler is a simple pass-through to the S3Helper, so just test that execution flows through
        // to the service as expected.

        // inputs
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload-id");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setUpload(upload2);

        // mock S3 helper
        S3Helper mockS3Helper = mock(S3Helper.class);
        when(mockS3Helper.readS3FileAsBytes(TestConstants.UPLOAD_BUCKET, "test-upload-id")).thenReturn(
                "test data".getBytes(Charsets.UTF_8));

        // set up test handler
        S3DownloadHandler handler = new S3DownloadHandler();
        handler.setS3Helper(mockS3Helper);

        // execute and validate
        handler.handle(ctx);
        assertEquals("test data", new String(ctx.getData(), Charsets.UTF_8));
    }

    @Test(expected = UploadValidationException.class)
    public void exception() throws Exception {
        // inputs
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload-id");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setUpload(upload2);

        // mock S3 helper
        S3Helper mockS3Helper = mock(S3Helper.class);
        when(mockS3Helper.readS3FileAsBytes(TestConstants.UPLOAD_BUCKET, "test-upload-id")).thenThrow(
                IOException.class);

        // set up test handler
        S3DownloadHandler handler = new S3DownloadHandler();
        handler.setS3Helper(mockS3Helper);

        // execute
        handler.handle(ctx);
    }
}
