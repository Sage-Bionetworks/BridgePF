package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UploadCertificateServiceImplTest {

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();
    private static final String CERT_BUCKET = CONFIG.getProperty("upload.cms.cert.bucket");
    private static final String PRIV_BUCKET = CONFIG.getProperty("upload.cms.priv.bucket");
    private static final String STUDY_ID = CONFIG.getEnvironment().name() + "-" + CONFIG.getUser();

    @Resource
    private AmazonS3 s3Client;

    @Resource
    private UploadCertificateService uploadCertificateService;

    @Before
    public void before() {
        assertNotNull(uploadCertificateService);
        assertNotNull(s3Client);
    }

    @After
    public void after() {
        s3Client.deleteObject(CERT_BUCKET, STUDY_ID + ".pem");
        s3Client.deleteObject(PRIV_BUCKET, STUDY_ID + ".pem");
    }

    @Test
    public void test() throws Exception {
        uploadCertificateService.createCmsKeyPair(STUDY_ID);
        S3Object certObject = s3Client.getObject(CERT_BUCKET, STUDY_ID + ".pem");
        assertNotNull(certObject);
        String certPem = readPem(certObject);
        X509Certificate cert = PemUtils.loadCertificateFromPem(certPem);
        cert.checkValidity();
        String commonName = "cn=" + STUDY_ID.toLowerCase() + CONFIG.getStudyHostnamePostfix().toLowerCase();
        assertTrue(cert.getSubjectX500Principal().getName().toLowerCase().contains(commonName));
        assertTrue(cert.getSubjectDN().getName().toLowerCase().contains(commonName));
        S3Object privObject = s3Client.getObject(PRIV_BUCKET, STUDY_ID + ".pem");
        assertNotNull(privObject);
        String privPem = readPem(privObject);
        PrivateKey privateKey = PemUtils.loadPrivateKeyFromPem(privPem);
        CmsEncryptor encryptor = new BcCmsEncryptor(cert, privateKey);
        assertNotNull(encryptor);
        assertEquals("something", new String(encryptor.decrypt(encryptor.encrypt("something".getBytes()))));
    }

    private String readPem(S3Object s3Obj) {
        try (S3ObjectInputStream ois = s3Obj.getObjectContent()) {
            return IOUtils.toString(ois, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanBucket(String bucket) {
        // Clean objects older than an hour
        ObjectListing objList = s3Client.listObjects(bucket);
        for (S3ObjectSummary obj: objList.getObjectSummaries()) {
            Date date = obj.getLastModified();
            Date now = DateTime.now(DateTimeZone.UTC).minusHours(1).toDate();
            if (now.after(date)) {
                s3Client.deleteObject(bucket, obj.getKey());
            }
        }
    }
}
