package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.base.Charsets;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class DecryptAndUnzipHandlerTest {
    @Test
    public void test() {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // inputs
        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(new DynamoStudy());
        ctx.setData("test data".getBytes(Charsets.UTF_8));

        // mock upload archive service
        UploadArchiveService mockSvc = mock(UploadArchiveService.class);

        // set up test handler
        DecryptAndUnzipHandler handler = new DecryptAndUnzipHandler();
        handler.setUploadArchiveService(mockSvc);

        // execute and validate
        handler.handle(ctx);
        verify(mockSvc).decryptAndUnzip(ctx.getStudy(), ctx.getData());
    }
}
