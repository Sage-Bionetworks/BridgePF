package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cms.CMSException;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.util.DuplicateZipEntryException;
import org.sagebionetworks.bridge.util.ZipOverflowException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Archives messages and then encrypts the archive using CMS.
 * Conversely also decrypts the archive and unpacks it.
 */
@Component
public class UploadArchiveService {
    private static final String CONFIG_KEY_MAX_NUM_ZIP_ENTRIES = "max.num.zip.entries";
    private static final String CONFIG_KEY_MAX_ZIP_ENTRY_SIZE = "max.zip.entry.size";

    // Temporary buffer size for unzipping, in bytes. This is big enough that there should be no churn for most files,
    // but small enough to have minimal memory overhead.
    private static final int TEMP_BUFFER_SIZE = 4096;

    private int maxNumZipEntries;
    private int maxZipEntrySize;
    private LoadingCache<String, CmsEncryptor> cmsEncryptorCache;

    /** Config, to get settings for zip. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig config) {
        maxNumZipEntries = config.getPropertyAsInt(CONFIG_KEY_MAX_NUM_ZIP_ENTRIES);
        maxZipEntrySize = config.getPropertyAsInt(CONFIG_KEY_MAX_ZIP_ENTRY_SIZE);
    }

    /** Loading cache for CMS encryptor, keyed by study ID. This is configured by Spring. */
    @Autowired
    public final void setCmsEncryptorCache(LoadingCache<String, CmsEncryptor> cmsEncryptorCache) {
        this.cmsEncryptorCache = cmsEncryptorCache;
    }

    /** Sets the max number of zip entries per archive. Separate setter so that tests can change this value. */
    public final void setMaxNumZipEntries(int maxNumZipEntries) {
        this.maxNumZipEntries = maxNumZipEntries;
    }

    /**
     * Sets the max number of uncompressed bytes per zip entry. Separate setter so that tests can change this value.
     */
    public final void setMaxZipEntrySize(int maxZipEntrySize) {
        this.maxZipEntrySize = maxZipEntrySize;
    }

    /**
     * Encrypts the specified data, using the encryption materials for the specified study.
     *
     * @param studyId
     *         study ID, must be non-null, non-empty, and refer to a valid study
     * @param bytes
     *         data to encrypt, must be non-null
     * @return encrypted data as a byte array
     * @throws BridgeServiceException
     *         if we fail to load the encryptor, or if encryption fails
     */
    public byte[] encrypt(String studyId, byte[] bytes) throws BridgeServiceException {
        // validate
        checkNotNull(studyId);
        checkArgument(StringUtils.isNotBlank(studyId));
        checkNotNull(bytes);

        // get encryptor from cache
        CmsEncryptor encryptor = getEncryptorForStudy(studyId);

        // encrypt
        byte[] encryptedData;
        try {
            encryptedData = encryptor.encrypt(bytes);
        } catch (CMSException | IOException ex) {
            throw new BridgeServiceException(ex);
        }
        return encryptedData;
    }

    /**
     * Decrypts the specified data, using the encryption materials for the specified study.
     *
     * @param studyId
     *         study ID, must be non-null, non-empty, and refer to a valid study
     * @param bytes
     *         data to decrypt, must be non-null
     * @return decrypted data as a byte array
     * @throws BridgeServiceException
     *         if we fail to load the encryptor, or if decryption fails
     */
    public byte[] decrypt(String studyId, byte[] bytes) throws BridgeServiceException {
        // validate
        checkNotNull(studyId);
        checkArgument(StringUtils.isNotBlank(studyId));
        checkNotNull(bytes);

        // decrypt
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             InputStream decryptedStream = decrypt(studyId, byteArrayInputStream)) {
            return ByteStreams.toByteArray(decryptedStream);
        } catch (IOException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    /**
     * Decrypts the specified data stream, using the encryption materials for the specified study, and returns the a
     * stream of decrypted data. The caller is responsible for closing both streams.
     */
    public InputStream decrypt(String studyId, InputStream source) {
        // validate
        checkNotNull(studyId);
        checkArgument(StringUtils.isNotBlank(studyId));
        checkNotNull(source);

        // get encryptor from cache
        CmsEncryptor encryptor = getEncryptorForStudy(studyId);

        // decrypt
        try {
            return encryptor.decrypt(source);
        } catch (CertificateEncodingException | CMSException | IOException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    /**
     * Helper function to get the encryptor for the given study.
     *
     * @param studyId
     *         study ID to get the encryptor for
     * @return the encryptor for the given study
     * @throws BridgeServiceException
     *         if we fail to load the encryptor, or if the encryptor can't be found
     */
    private CmsEncryptor getEncryptorForStudy(String studyId) throws BridgeServiceException {
        CmsEncryptor encryptor;
        try {
            encryptor = cmsEncryptorCache.get(studyId);
        } catch (ExecutionException | UncheckedExecutionException ex) {
            throw new BridgeServiceException(ex);
        }
        if (encryptor == null) {
            throw new BridgeServiceException(String.format("No encrypt for study %s", studyId));
        }
        return encryptor;
    }

    /**
     * Zips the given archive entries into a raw byte array.
     *
     * @param dataMap
     *         entries to zip, keyed by filename, must be non-null
     * @return byte array of data representing the zipped entries
     * @throws BridgeServiceException
     *         if zipping fails
     */
    public byte[] zip(Map<String, byte[]> dataMap) throws BridgeServiceException {
        // Validate input
        checkNotNull(dataMap);

        // Zip
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
        } catch (IOException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    /**
     * <p>
     * Unzips the given byte array. The resulting map keys are the filenames of the data entries. The values are the
     * unzipped data entries as a byte array.
     * </p>
     * <p>
     * This method will throw a BadRequestException if the zip file somehow contains duplicate filenames.
     * </p>
     *
     * @param bytes
     *         byte array containing the raw data to unzip, must be non-null
     * @return raw bytes of unzipped data, keyed by filename
     * @throws BridgeServiceException
     *         if unzipping fails
     */
    public Map<String, byte[]> unzip(byte[] bytes) throws BridgeServiceException {
        // Validate input
        checkNotNull(bytes);

        // Unzip
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
            return dataMap;
        } catch (IOException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    /**
     * <p>
     * Unzips the given stream. For each individual zip entry, this method will call entryNameToOutputStream, passing
     * in the zip entry file name and expecting an OutputStream in which to write the unzipped bytes. It will then call
     * outputStreamFinalizer, allowing the caller to finalize the stream, for example, closing the stream.
     * </p>
     * <p>
     * The caller is responsible for closing any and all streams involved.
     * </p>
     *
     * @param source
     *         input stream of zipped data to unzip
     * @param entryNameToOutpuStream
     *         arg is the zip entry file name, return value is the OutputStream in which to write the unzipped bytes
     * @param outputStreamFinalizer
     *         args are the zip entry file name and the corresponding OutputStream returned by entryNameToOutputStream;
     *         this is where you finalize the stream, eg closing the stream
     */
    public void unzip(InputStream source, Function<String, OutputStream> entryNameToOutpuStream,
            BiConsumer<String, OutputStream> outputStreamFinalizer) {
        // Validate input
        checkNotNull(source);
        checkNotNull(entryNameToOutpuStream);
        checkNotNull(outputStreamFinalizer);

        // Unzip
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
        } catch (DuplicateZipEntryException | ZipOverflowException ex) {
            throw new BadRequestException(ex);
        } catch (IOException ex) {
            throw new BridgeServiceException(ex);
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
