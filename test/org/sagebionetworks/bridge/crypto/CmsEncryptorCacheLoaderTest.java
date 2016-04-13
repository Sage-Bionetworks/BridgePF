package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;

import com.google.common.base.Charsets;
import org.junit.Test;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.springframework.core.io.ClassPathResource;

public class CmsEncryptorCacheLoaderTest {
    @Test
    public void test() throws Exception {
        // Test strategy is to validate that we can successfully create a CmsEncryptor. S3Helper is mocked to return
        // test materials instead of calling through to S3.

        // set up cert and priv key PEM files as strings
        File certFile =  new ClassPathResource("/cms/rsacert.pem").getFile();
        byte[] certBytes = Files.readAllBytes(certFile.toPath());
        String certString = new String(certBytes, Charsets.UTF_8);
        File privKeyFile =  new ClassPathResource("/cms/rsaprivkey.pem").getFile();
        byte[] privKeyBytes = Files.readAllBytes(privKeyFile.toPath());
        String privKeyString = new String(privKeyBytes, Charsets.UTF_8);

        // mock S3 helper
        S3Helper s3Helper = mock(S3Helper.class);
        when(s3Helper.readS3FileAsString(CmsEncryptorCacheLoader.CERT_BUCKET, "test-study.pem")).thenReturn(
                certString);
        when(s3Helper.readS3FileAsString(CmsEncryptorCacheLoader.PRIV_KEY_BUCKET, "test-study.pem")).thenReturn(
                privKeyString);

        // set up cache loader
        CmsEncryptorCacheLoader testCacheLoader = new CmsEncryptorCacheLoader();
        testCacheLoader.setS3CmsHelper(s3Helper);

        // execute and validate
        CmsEncryptor retVal = testCacheLoader.load("test-study");
        assertNotNull(retVal);
    }
}
