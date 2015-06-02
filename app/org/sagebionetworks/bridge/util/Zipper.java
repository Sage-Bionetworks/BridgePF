package org.sagebionetworks.bridge.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;

public class Zipper {

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
        int offset = 0;
        int length = 10000;
        byte[] bytes = new byte[length];
        int read = inputStream.read(bytes, offset, length - offset);
        while (read >= 0) {
            offset = offset + read;
            if (offset > maxZipEntrySize) {
                throw new ZipOverflowException("Zip entry size is over the max allowed size. The entry " + entryName +
                        " has size more than " + offset + ". The max allowed size is" + maxZipEntrySize + ".");
            }
            length = length * 3 / 2 + 1;
            byte[] newBytes = new byte[length];
            System.arraycopy(bytes, 0, newBytes, 0, offset);
            bytes = newBytes;
            read = inputStream.read(bytes, offset, length - offset);
        }
        byte[] newBytes = new byte[offset];
        System.arraycopy(bytes, 0, newBytes, 0, offset);
        return newBytes;
    }
}
