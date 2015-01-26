package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import com.google.common.cache.LoadingCache;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.ArchiveEntry;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("unchecked")
public class UploadArchiveServiceTest {

    private UploadArchiveService archiveService;
    private ArchiveEntry entryInfo;
    private ArchiveEntry entry0;
    private ArchiveEntry entry1;
    private Study testStudy;

    @Before
    public void before() throws Exception {
        // test study
        testStudy = new DynamoStudy();
        testStudy.setIdentifier("test-study");

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

        ObjectMapper objectMapper = new ObjectMapper();

        File file = new File("test/resources/cms/data/info.json");
        entryInfo = new ArchiveEntry(file.getName(), objectMapper.readTree(file));
        file = new File("test/resources/cms/data/tapTheButton0.json");
        entry0 = new ArchiveEntry(file.getName(), objectMapper.readTree(file));
        file = new File("test/resources/cms/data/tapTheButton1.json");
        entry1 = new ArchiveEntry(file.getName(), objectMapper.readTree(file));
    }

    @Test
    public void test() throws Exception {
        List<ArchiveEntry> entries = new ArrayList<>();
        entries.add(entry0);
        entries.add(entry1);
        entries.add(entryInfo);
        byte[] archive = archiveService.zipAndEncrypt(testStudy, entries);
        List<ArchiveEntry> results = archiveService.decryptAndUnzip(testStudy, archive);
        assertNotNull(results);
        assertEquals(3, results.size());
        for (ArchiveEntry archiveEntry : results) {
            assertNotNull(archiveEntry);
        }
    }

    @Test
    public void testDecryptAndUnzip() throws Exception {
        File archiveFile = new File("test/resources/cms/data/archive");
        byte[] encryptedBytes = Files.readAllBytes(archiveFile.toPath());
        List<ArchiveEntry> archive = archiveService.decryptAndUnzip(testStudy, encryptedBytes);
        assertNotNull(archive);
        assertEquals(3, archive.size());
        for (ArchiveEntry archiveEntry : archive) {
            assertNotNull(archiveEntry);
        }
    }
}
