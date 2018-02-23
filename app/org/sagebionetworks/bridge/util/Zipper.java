package org.sagebionetworks.bridge.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
     */
    public Map<String, byte[]> unzip(@Nonnull byte[] bytes)
            throws IOException, ZipOverflowException, DuplicateZipEntryException {
        Map<String, byte[]> dataMap = new HashMap<>();
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            unzip(bais,
                    // We use Apache IO's ByteArrayOutputStream, because it's memory optimized, so we don't have to
                    // clean up a bunch of byte arrays.
                    entryName -> new ByteArrayOutputStream(),
                    (entryName, outputStream) -> {
                        byte[] unzippedFileContent = ((ByteArrayOutputStream) outputStream).toByteArray();
                        dataMap.put(entryName, unzippedFileContent);
                        try {
                            outputStream.close();
                        } catch (IOException ex) {
                            // BiConsumer doesn't throw, so wrap this in a RuntimeException.
                            throw new RuntimeException(ex);
                        }
                    });
        }
        return dataMap;
    }

    // todo doc
    public void unzip(InputStream source, Function<String, OutputStream> entryNameToOutpuStream,
            BiConsumer<String, OutputStream> outputStreamFinalizer)
            throws DuplicateZipEntryException, IOException, ZipOverflowException {
        Set<String> zipEntryNameSet = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(source)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (zipEntryNameSet.size() >= maxNumZipEntries) {
                    throw new ZipOverflowException("The number of zip entries is over the max allowed");
                }
                final String entryName = zipEntry.getName();
                if (zipEntryNameSet.contains(entryName)) {
                    throw new DuplicateZipEntryException("Duplicate filename " + entryName);
                }
                final long entrySize = zipEntry.getSize();
                if (entrySize > maxZipEntrySize) {
                    throw new ZipOverflowException("Zip entry size is over the max allowed size. The entry " +
                            entryName + " has size " + entrySize + ". The max allowed size is" + maxZipEntrySize +
                            ".");
                }
                zipEntryNameSet.add(entryName);
                OutputStream outputStream = entryNameToOutpuStream.apply(entryName);
                toByteArray(entryName, zis, outputStream);
                outputStreamFinalizer.accept(entryName, outputStream);
                zipEntry = zis.getNextEntry();
            }
        }
    }

    private void toByteArray(String entryName, InputStream inputStream, OutputStream outputStream)
            throws IOException, ZipOverflowException {
        // We want copy data from the stream to a byte array manually, so we can count the bytes and protect against
        // zip bombs.
        byte[] tempBuffer = new byte[TEMP_BUFFER_SIZE];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(tempBuffer, 0, TEMP_BUFFER_SIZE)) >= 0) {
            totalBytes += bytesRead;
            if (totalBytes > maxZipEntrySize) {
                throw new ZipOverflowException("Zip entry size is over the max allowed size. The entry " + entryName +
                        " has size more than " + totalBytes + ". The max allowed size is" + maxZipEntrySize + ".");
            }

            outputStream.write(tempBuffer, 0, bytesRead);
        }
    }
}
