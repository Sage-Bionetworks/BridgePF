package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import java.io.File;

import com.google.common.base.Charsets;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.s3.S3Helper;

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

        // Set up file helper and temp dir
        InMemoryFileHelper inMemoryFileHelper = new InMemoryFileHelper();
        File tmpDir = inMemoryFileHelper.createTempDir();
        ctx.setTempDir(tmpDir);

        // mock S3 helper
        S3Helper mockS3Helper = mock(S3Helper.class);
        doAnswer(invocation -> {
            File destFile = invocation.getArgument(2);
            inMemoryFileHelper.writeBytes(destFile, "test data".getBytes(Charsets.UTF_8));
            return null;
        }).when(mockS3Helper).downloadS3File(eq(TestConstants.UPLOAD_BUCKET), eq("test-upload-id"), any());

        // set up test handler
        S3DownloadHandler handler = new S3DownloadHandler();
        handler.setFileHelper(inMemoryFileHelper);
        handler.setS3Helper(mockS3Helper);

        // execute and validate
        handler.handle(ctx);
        byte[] dataFileContent = inMemoryFileHelper.getBytes(ctx.getDataFile());
        assertEquals("test data", new String(dataFileContent, Charsets.UTF_8));
    }
}
