package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class UnzipHandlerTest {
    private static final String ZIPPED_FILE_NAME = "test.zip";
    private static final byte[] ZIPPED_FILE_DUMMY_CONTENT = "zipped test data".getBytes(Charsets.UTF_8);

    @Test
    public void test() throws Exception {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // Set up File Helper and input file.
        InMemoryFileHelper inMemoryFileHelper = new InMemoryFileHelper();
        File tmpDir = inMemoryFileHelper.createTempDir();
        File zippedDataFile = inMemoryFileHelper.newFile(tmpDir, ZIPPED_FILE_NAME);
        inMemoryFileHelper.writeBytes(zippedDataFile, ZIPPED_FILE_DUMMY_CONTENT);

        // inputs
        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setTempDir(tmpDir);
        ctx.setDecryptedDataFile(zippedDataFile);

        // mock UploadArchiveService
        Map<String, byte[]> mockUnzippedDataMap = ImmutableMap.of(
                "foo", "foo data".getBytes(Charsets.UTF_8),
                "bar", "bar data".getBytes(Charsets.UTF_8),
                "baz", "baz data".getBytes(Charsets.UTF_8));

        UploadArchiveService mockSvc = mock(UploadArchiveService.class);
        doAnswer(invocation -> {
            Function<String, OutputStream> entryNameToOutputStream = invocation.getArgument(1);
            BiConsumer<String, OutputStream> outputStreamFinalizer = invocation.getArgument(2);

            // Mimic unzip by writing the unzipped data to the output stream.
            for (Map.Entry<String, byte[]> oneUnzippedDataEntry : mockUnzippedDataMap.entrySet()) {
                String entryName = oneUnzippedDataEntry.getKey();
                byte[] mockContent = oneUnzippedDataEntry.getValue();

                OutputStream unzipOutputStream = entryNameToOutputStream.apply(entryName);
                unzipOutputStream.write(mockContent);
                outputStreamFinalizer.accept(entryName, unzipOutputStream);
            }

            // Required return.
            return null;
        }).when(mockSvc).unzip(any(), any(), any());

        // set up test handler
        UnzipHandler handler = new UnzipHandler();
        handler.setFileHelper(inMemoryFileHelper);
        handler.setUploadArchiveService(mockSvc);

        // execute and validate
        handler.handle(ctx);
        Map<String, File> unzippedFileMap = ctx.getUnzippedDataFileMap();
        assertEquals(mockUnzippedDataMap.size(), unzippedFileMap.size());
        for (String oneUnzippedFileName : mockUnzippedDataMap.keySet()) {
            File unzippedFile = unzippedFileMap.get(oneUnzippedFileName);
            byte[] unzippedFileContent = inMemoryFileHelper.getBytes(unzippedFile);
            assertArrayEquals(mockUnzippedDataMap.get(oneUnzippedFileName), unzippedFileContent);
        }

        // verify stream passed into mockSvc
        ArgumentCaptor<InputStream> zippedFileInputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mockSvc).unzip(zippedFileInputStreamCaptor.capture(), any(), any());

        InputStream zippedFileInputStream = zippedFileInputStreamCaptor.getValue();
        byte[] zippedFileInputStreamContent = ByteStreams.toByteArray(zippedFileInputStream);
        assertArrayEquals(ZIPPED_FILE_DUMMY_CONTENT, zippedFileInputStreamContent);
    }
}
