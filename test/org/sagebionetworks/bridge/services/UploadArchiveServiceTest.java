package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.cache.LoadingCache;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.springframework.core.io.ClassPathResource;

@SuppressWarnings("unchecked")
public class UploadArchiveServiceTest {
    private static final byte[] PLAIN_TEXT_DATA = "This is my raw data".getBytes(Charsets.UTF_8);

    private static UploadArchiveService archiveService;
    private static byte[] encryptedData;

    @BeforeClass
    public static void before() throws Exception {
        // encryptor
        File certFile = new ClassPathResource("/cms/rsacert.pem").getFile();
        byte[] certBytes = Files.readAllBytes(certFile.toPath());
        X509Certificate cert = PemUtils.loadCertificateFromPem(new String(certBytes));
        File privateKeyFile = new ClassPathResource("/cms/rsaprivkey.pem").getFile();
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        PrivateKey privateKey = PemUtils.loadPrivateKeyFromPem(new String(privateKeyBytes));
        CmsEncryptor encryptor = new BcCmsEncryptor(cert, privateKey);

        // mock encryptor cache
        LoadingCache<String, CmsEncryptor> mockEncryptorCache = mock(LoadingCache.class);
        when(mockEncryptorCache.get(notNull())).thenReturn(encryptor);

        // archive service
        archiveService = new UploadArchiveService();
        archiveService.setCmsEncryptorCache(mockEncryptorCache);
        archiveService.setMaxNumZipEntries(1000000);
        archiveService.setMaxZipEntrySize(1000000);

        // Encrypt some data, so our tests have something to work with.
        encryptedData = archiveService.encrypt("test-study", PLAIN_TEXT_DATA);
    }

    @Test
    public void encryptSuccess() {
        assertNotNull(encryptedData);
        assertTrue(encryptedData.length > 0);
    }

    @Test(expected = NullPointerException.class)
    public void encryptNullStudyId() {
        archiveService.encrypt(null, PLAIN_TEXT_DATA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryptEmptyStudyId() {
        archiveService.encrypt("", PLAIN_TEXT_DATA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryptBlankStudyId() {
        archiveService.encrypt("   ", PLAIN_TEXT_DATA);
    }

    @Test(expected = NullPointerException.class)
    public void encryptNullBytes() {
        archiveService.encrypt("test-study", null);
    }

    @Test
    public void decryptSuccess() {
        byte[] decryptedData = archiveService.decrypt("test-study", encryptedData);
        assertArrayEquals(PLAIN_TEXT_DATA, decryptedData);
    }

    @Test(expected = BridgeServiceException.class)
    public void decryptGarbageData() {
        String garbageStr = "This is not encrypted data.";
        byte[] garbageData = garbageStr.getBytes(Charsets.UTF_8);
        archiveService.decrypt("test-study", garbageData);
    }

    @Test(expected = NullPointerException.class)
    public void decryptBytesNullStudyId() {
        archiveService.decrypt(null, encryptedData);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decryptBytesEmptyStudyId() {
        archiveService.decrypt("", encryptedData);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decryptBytesBlankStudyId() {
        archiveService.decrypt("   ", encryptedData);
    }

    @Test(expected = NullPointerException.class)
    public void decryptBytesNullBytes() {
        archiveService.decrypt("test-study", (byte[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void decryptStreamNullStudyId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt(null, encryptedInputStream);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void decryptStreamEmptyStudyId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt("", encryptedInputStream);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void decryptStreamBlankStudyId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt("   ", encryptedInputStream);
        }
    }

    @Test(expected = NullPointerException.class)
    public void decryptStreamNullBytes() {
        archiveService.decrypt("test-study", (InputStream) null);
    }

    @Test
    public void decryptAndUnzipRealFile() throws Exception {
        // get archive file, which is stored in git
        File archiveFile = new ClassPathResource("/cms/data/archive").getFile();
        byte[] encryptedBytes = Files.readAllBytes(archiveFile.toPath());

        // decrypt
        byte[] decryptedData = archiveService.decrypt("test-study", encryptedBytes);
        assertNotNull(decryptedData);
        assertTrue(decryptedData.length > 0);

        // unzip
        Map<String, byte[]> unzippedData = archiveService.unzip(decryptedData);
        assertEquals(3, unzippedData.size());
        for (byte[] oneData : unzippedData.values()) {
            assertNotNull(oneData);
            assertTrue(oneData.length > 0);
        }
    }
}
