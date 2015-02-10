package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

@SuppressWarnings("unchecked")
public class UploadArchiveServiceTest {
    private UploadArchiveService archiveService;

    @Before
    public void before() throws Exception {
        // encryptor
        File certFile = new File("test/resources/cms/rsacert.pem");
        byte[] certBytes = Files.readAllBytes(certFile.toPath());
        X509Certificate cert = PemUtils.loadCertificateFromPem(new String(certBytes));
        File privateKeyFile = new File("test/resources/cms/rsaprivkey.pem");
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        PrivateKey privateKey = PemUtils.loadPrivateKeyFromPem(new String(privateKeyBytes));
        CmsEncryptor encryptor = new BcCmsEncryptor(cert, privateKey);

        // mock encryptor cache
        LoadingCache<String, CmsEncryptor> mockEncryptorCache = mock(LoadingCache.class);
        when(mockEncryptorCache.get(notNull(String.class))).thenReturn(encryptor);

        // archive service
        archiveService = new UploadArchiveService();
        archiveService.setCmsEncryptorCache(mockEncryptorCache);
    }

    @Test
    public void encryptDecryptRoundTrip() {
        // starting data
        String inputStr = "This is my raw data.";
        byte[] inputData = inputStr.getBytes(Charsets.UTF_8);

        // encrypt
        byte[] encryptedData = archiveService.encrypt("test-study", inputData);
        assertNotNull(encryptedData);
        assertTrue(encryptedData.length > 0);

        // decrypt
        byte[] decryptedData = archiveService.decrypt("test-study", encryptedData);
        assertEquals(inputStr, new String(decryptedData, Charsets.UTF_8));
    }

    @Test(expected = BridgeServiceException.class)
    public void decryptGarbageData() {
        String garbageStr = "This is not encrypted data.";
        byte[] garbageData = garbageStr.getBytes(Charsets.UTF_8);
        archiveService.decrypt("test-study", garbageData);
    }

    @Test
    public void zipUnzipRoundTrip() {
        // starting data
        Map<String, byte[]> inputMap = ImmutableMap.of(
                "foo", "foo data".getBytes(Charsets.UTF_8),
                "bar", "bar data".getBytes(Charsets.UTF_8),
                "baz", "baz data".getBytes(Charsets.UTF_8));

        // zip
        byte[] zippedData = archiveService.zip(inputMap);
        assertNotNull(zippedData);
        assertTrue(zippedData.length > 0);

        // unzip
        Map<String, byte[]> unzippedData = archiveService.unzip(zippedData);
        assertEquals(3, unzippedData.size());
        assertArrayEquals(inputMap.get("foo"), unzippedData.get("foo"));
        assertArrayEquals(inputMap.get("bar"), unzippedData.get("bar"));
        assertArrayEquals(inputMap.get("baz"), unzippedData.get("baz"));
    }

    // There was originally a test here for unzipping garbage data. However, it looks like Java
    // ZipInputStream.getNextEntry() will just return null if the stream contains garbage data.

    @Test
    public void decryptAndUnzipRealFile() throws Exception {
        // get archive file, which is stored in git
        File archiveFile = new File("test/resources/cms/data/archive");
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
