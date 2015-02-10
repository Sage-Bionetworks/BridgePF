package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class DecryptHandlerTest {
    @Test
    public void test() {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // inputs
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test-study");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setData("encrypted test data".getBytes(Charsets.UTF_8));

        // mock UploadArchiveService
        UploadArchiveService mockSvc = mock(UploadArchiveService.class);
        when(mockSvc.decrypt("test-study", ctx.getData())).thenReturn("decrypted test data".getBytes(Charsets.UTF_8));

        // set up test handler
        DecryptHandler handler = new DecryptHandler();
        handler.setUploadArchiveService(mockSvc);

        // execute and validate
        handler.handle(ctx);
        assertEquals("decrypted test data", new String(ctx.getDecryptedData(), Charsets.UTF_8));
    }
}
