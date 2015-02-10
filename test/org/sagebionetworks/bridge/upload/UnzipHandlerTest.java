package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.services.UploadArchiveService;

public class UnzipHandlerTest {
    @Test
    public void test() {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // inputs
        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setDecryptedData("zipped test data".getBytes(Charsets.UTF_8));

        // mock UploadArchiveService
        Map<String, byte[]> mockUnzippedDataMap = ImmutableMap.of(
                "foo", "foo data".getBytes(Charsets.UTF_8),
                "bar", "bar data".getBytes(Charsets.UTF_8),
                "baz", "baz data".getBytes(Charsets.UTF_8));

        UploadArchiveService mockSvc = mock(UploadArchiveService.class);
        when(mockSvc.unzip(ctx.getDecryptedData())).thenReturn(mockUnzippedDataMap);

        // set up test handler
        UnzipHandler handler = new UnzipHandler();
        handler.setUploadArchiveService(mockSvc);

        // execute and validate
        handler.handle(ctx);
        Map<String, byte[]> retVal = ctx.getUnzippedDataMap();
        assertEquals(3, retVal.size());
        assertArrayEquals(mockUnzippedDataMap.get("foo"), retVal.get("foo"));
        assertArrayEquals(mockUnzippedDataMap.get("bar"), retVal.get("bar"));
        assertArrayEquals(mockUnzippedDataMap.get("baz"), retVal.get("baz"));
    }
}
