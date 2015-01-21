package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cms.CMSException;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.ArchiveEntry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Archives messages and then encrypts the archive using CMS.
 * Conversely also decrypts the archive and unpacks it.
 */
@Component
public class UploadArchiveService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LoadingCache<String, CmsEncryptor> cmsEncryptorCache;

    /** Loading cache for CMS encryptor, keyed by study ID. This is configured by Spring. */
    @Autowired
    public void setCmsEncryptorCache(LoadingCache<String, CmsEncryptor> cmsEncryptorCache) {
        this.cmsEncryptorCache = cmsEncryptorCache;
    }

    public byte[] zipAndEncrypt(Study study, List<ArchiveEntry> entries) {
        checkNotNull(study);
        checkNotNull(entries);
        byte[] zipped = zip(entries);
        return encrypt(study, zipped);
    }

    public List<ArchiveEntry> decryptAndUnzip(Study study, byte[] bytes) {
        checkNotNull(bytes);
        byte[] decrypted = decrypt(study, bytes);
        return unzip(decrypted);
    }

    private byte[] encrypt(Study study, byte[] bytes) {
        try {
            CmsEncryptor encryptor = cmsEncryptorCache.get(study.getIdentifier());
            return encryptor.encrypt(bytes);
        } catch (CMSException | ExecutionException | IOException | UncheckedExecutionException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    private byte[] decrypt(Study study, byte[] bytes) {
        try {
            CmsEncryptor encryptor = cmsEncryptorCache.get(study.getIdentifier());
            return encryptor.decrypt(bytes);
        } catch (CertificateEncodingException | CMSException | ExecutionException | IOException |
                UncheckedExecutionException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    private byte[] zip(final List<ArchiveEntry> entries) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ArchiveEntry archiveEntry : entries) {
                ZipEntry zipEntry = new ZipEntry(archiveEntry.getName());
                zos.putNextEntry(zipEntry);
                byte[] bytes = objectMapper.writeValueAsBytes(archiveEntry.getContent());
                zos.write(bytes);
                zos.closeEntry();
            }
            zos.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new BridgeServiceException(ex);
        }
    }

    private List<ArchiveEntry> unzip(final byte[] bytes) {
        final List<ArchiveEntry> archive = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while(zipEntry != null) {
                byte[] content = IOUtils.toByteArray(zis);
                JsonNode node = objectMapper.readTree(content);
                archive.add(new ArchiveEntry(zipEntry.getName(), node));
                zipEntry = zis.getNextEntry();
            }
            return archive;
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
}
