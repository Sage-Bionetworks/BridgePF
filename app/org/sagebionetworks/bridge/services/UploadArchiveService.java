package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cms.CMSException;
import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.models.upload.ArchiveEntry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Archives messages and then encrypts the archive using CMS.
 * Conversely also decrypts the archive and unpacks it.
 */
public class UploadArchiveService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CmsEncryptor encryptor;

    public UploadArchiveService(X509Certificate cert, PrivateKey privateKey) {
        try {
            encryptor = new BcCmsEncryptor(cert, privateKey);
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] zipAndEncrypt(List<ArchiveEntry> entries) {
        checkNotNull(entries);
        byte[] zipped = zip(entries);
        return encrypt(zipped);
    }

    public List<ArchiveEntry> decryptAndUnzip(byte[] bytes) {
        checkNotNull(bytes);
        byte[] decrypted = decrypt(bytes);
        return unzip(decrypted);
    }

    private byte[] encrypt(byte[] bytes) {
        try {
            return encryptor.encrypt(bytes);
        } catch (CMSException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decrypt(byte[] bytes) {
        try {
            return encryptor.decrypt(bytes);
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        } catch (CMSException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ArchiveEntry> unzip(final byte[] bytes) {
        final List<ArchiveEntry> archive = new ArrayList<ArchiveEntry>();
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
            throw new RuntimeException(e);
        }
    }
}
