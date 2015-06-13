package org.sagebionetworks.bridge.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;

import org.apache.commons.io.output.ByteArrayOutputStream;

public class Zipper {
    // Size of temporary buffer in bytes. This is big enough that there should be no churn for most files, but small
    // enough to have minimal memory overhead.
    private static final int TEMP_BUFFER_SIZE = 4096;

    /** Max number of uncompressed bytes per zip entry. */
    private final int maxZipEntrySize;

    /** Max number of zip entries per archive. */
    private final int maxNumZipEntries;

    public Zipper(int maxZipEntrySize, int maxNumZipEntries) {
        checkArgument(maxZipEntrySize > 0);
        checkArgument(maxNumZipEntries > 0);
        this.maxZipEntrySize = maxZipEntrySize;
        this.maxNumZipEntries = maxNumZipEntries;
    }

    /**
     * Zips the given archive entries into a raw byte array. Each entry
     * is a byte array of data keyed by file name.
     */
    public byte[] zip(@Nonnull Map<String, byte[]> dataMap) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> oneData : dataMap.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(oneData.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(oneData.getValue());
                zos.closeEntry();
            }
            zos.flush();
            return baos.toByteArray();
        }
    }

    /**
     * Unzips the given byte array. The resulting map keys are the filenames of the data entries. The values are the
     * unzipped data entries as a byte array.
     * @param bytes
     * @return
     * @throws ZipOverflowException 
     * @throws DuplicateZipEntryException 
     */
    @SuppressWarnings("resource")
    public Map<String, byte[]> unzip(@Nonnull byte[] bytes)
            throws IOException, ZipOverflowException, DuplicateZipEntryException {
        final Map<String, byte[]> dataMap = new HashMap<>();
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                final ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (dataMap.size() >= maxNumZipEntries) {
                    throw new ZipOverflowException("The number of zip entries is over the max allowed");
                }
                final String entryName = zipEntry.getName();
                if (dataMap.containsKey(entryName)) {
                    throw new DuplicateZipEntryException(String.format("Duplicate filename %s", entryName));
                }
                final long entrySize = zipEntry.getSize();
                if (entrySize > maxZipEntrySize) {
                    throw new ZipOverflowException("Zip entry size is over the max allowed size. The entry " + entryName +
                            " has size " + entrySize + ". The max allowed size is" + maxZipEntrySize + ".");
                }
                byte[] content = toByteArray(entryName, zis);
                dataMap.put(entryName, content);
                zipEntry = zis.getNextEntry();
            }
            return dataMap;
        }
    }

    private byte[] toByteArray(final String entryName, final InputStream inputStream)
            throws IOException, ZipOverflowException {
        // We want copy data from the stream to a byte array manually, so we can count the bytes and protect against
        // zip bombs. We use Apache IO's ByteArrayOutputStream, because it's memory optimized, so we don't have to
        // clean up a bunch of byte arrays.
        byte[] tempBuffer = new byte[TEMP_BUFFER_SIZE];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(tempBuffer, 0, TEMP_BUFFER_SIZE)) >= 0) {
            totalBytes += bytesRead;
            if (totalBytes > maxZipEntrySize) {
                throw new ZipOverflowException("Zip entry size is over the max allowed size. The entry " + entryName +
                        " has size more than " + totalBytes + ". The max allowed size is" + maxZipEntrySize + ".");
            }

            baos.write(tempBuffer, 0, bytesRead);
        }

        return baos.toByteArray();
    }
}
