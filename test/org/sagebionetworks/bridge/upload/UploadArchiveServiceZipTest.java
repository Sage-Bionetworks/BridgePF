package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class UploadArchiveServiceZipTest {
    private static final Map<String, byte[]> UNZIPPED_FILE_MAP = ImmutableMap.<String, byte[]>builder()
            .put("foo", "foo data".getBytes(Charsets.UTF_8)).put("bar", "bar data".getBytes(Charsets.UTF_8))
            .put("baz", "baz data".getBytes(Charsets.UTF_8)).build();

    private static UploadArchiveService uploadArchiveService;
    private static byte[] zippedData;

    @BeforeClass
    public static void beforeClass() {
        // Make upload archive service.
        uploadArchiveService = new UploadArchiveService();
        uploadArchiveService.setMaxZipEntrySize(1000000);
        uploadArchiveService.setMaxNumZipEntries(1000000);

        // Zip some data, so our tests have something to work with.
        zippedData = uploadArchiveService.zip(UNZIPPED_FILE_MAP);
    }

    @Test
    public void zipSuccess() {
        assertNotNull(zippedData);
        assertTrue(zippedData.length > 0);
    }

    @Test(expected = NullPointerException.class)
    public void zipNullInput() {
        uploadArchiveService.zip(null);
    }

    @Test
    public void unzipBytesSuccess() {
        Map<String, byte[]> result = uploadArchiveService.unzip(zippedData);

        // Need to check each entry in the map, since byte[].equals() doesn't do what you expect.
        assertEquals(UNZIPPED_FILE_MAP.size(), result.size());
        for (String oneUnzippedFileName : UNZIPPED_FILE_MAP.keySet()) {
            assertArrayEquals(UNZIPPED_FILE_MAP.get(oneUnzippedFileName), result.get(oneUnzippedFileName));
        }
    }

    // There was originally a test here for unzipping garbage data. However, it looks like Java
    // ZipInputStream.getNextEntry() will just return null if the stream contains garbage data.

    @Test(expected = NullPointerException.class)
    public void unzipBytesNullInput() {
        uploadArchiveService.unzip(null);
    }

    @Test(expected=BadRequestException.class)
    public void testTooManyZipEntries() throws Exception {
        // Set up a test service just for this test. Set the max num to something super small, like 2.
        UploadArchiveService testSvc = new UploadArchiveService();
        testSvc.setMaxNumZipEntries(2);
        testSvc.setMaxZipEntrySize(1000000);

        // Execute - will throw
        testSvc.unzip(zippedData);
    }

    @Test(expected=BadRequestException.class)
    public void testZipEntryTooBig() throws Exception {
        // Set up a test service just for this test. Set the max size to something super small, like 6.
        UploadArchiveService testSvc = new UploadArchiveService();
        testSvc.setMaxNumZipEntries(1000000);
        testSvc.setMaxZipEntrySize(6);

        // Execute - will throw
        testSvc.unzip(zippedData);
    }

    @Test(expected = NullPointerException.class)
    public void unzipStreamNullStream() {
        uploadArchiveService.unzip(null,
                // Use NullOutputStream, since the test won't ever actually create any streams.
                entryName -> ByteStreams.nullOutputStream(),
                // Do nothing, since the output stream isn't a real output stream.
                (entryName, outputStream) -> {});
    }

    @Test(expected = NullPointerException.class)
    public void unzipStreamNullOutputStreamFunction() throws Exception {
        try (ByteArrayInputStream zippedDataInputStream = new ByteArrayInputStream(zippedData)) {
            uploadArchiveService.unzip(zippedDataInputStream,
                    null,
                    // Do nothing, since the output stream isn't a real output stream.
                    (entryName, outputStream) -> {});
        }
    }

    @Test(expected = NullPointerException.class)
    public void unzipStreamNullOutputStreamFinalizer() throws Exception {
        try (ByteArrayInputStream zippedDataInputStream = new ByteArrayInputStream(zippedData)) {
            uploadArchiveService.unzip(zippedDataInputStream,
                    // Use NullOutputStream, since the test won't ever actually create any streams.
                    entryName -> ByteStreams.nullOutputStream(),
                    null);
        }
    }
}
