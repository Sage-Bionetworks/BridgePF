package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class UploadCertificateServiceMockTest {
    // For safety, don't use api study. Otherwise, if we botch the test, we risk stomping over the api PEM keys again.
    private static final String STUDY_ID_STRING = "cert-test-study";
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(STUDY_ID_STRING);
    private static final String PEM_FILENAME = UploadCertificateService.getPemFilename(STUDY_ID);

    private AmazonS3 mockS3client;
    private UploadCertificateService svc;

    @Before
    public void before() {
        // Mock S3 client. We'll fill in the details during the test.
        mockS3client = mock(AmazonS3.class);

        // Spy UploadCertificateService. This allows us to mock s3Put without doing a bunch of complex logic.
        svc = spy(new UploadCertificateService());
        svc.setS3CmsClient(mockS3client);
        doNothing().when(svc).s3Put(any(), any(), any());
    }

    @Test
    public void createKeyPair() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(false);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                false);
        testCreateKeyPair();
    }

    @Test
    public void certExists() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(true);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                false);
        testCreateKeyPair();
    }

    @Test
    public void privKeyExists() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(false);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                true);
        testCreateKeyPair();
    }

    private void testCreateKeyPair() {
        // execute
        svc.createCmsKeyPair(STUDY_ID);

        // verify key pair were created
        ArgumentCaptor<String> certCaptor = ArgumentCaptor.forClass(String.class);
        verify(svc).s3Put(eq(UploadCertificateService.CERT_BUCKET), eq(PEM_FILENAME), certCaptor.capture());
        String cert = certCaptor.getValue();
        assertTrue(cert.contains("-----BEGIN CERTIFICATE-----"));
        assertTrue(cert.contains("-----END CERTIFICATE-----"));

        ArgumentCaptor<String> privKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(svc).s3Put(eq(UploadCertificateService.PRIVATE_KEY_BUCKET), eq(PEM_FILENAME), privKeyCaptor.capture());
        String privKey = privKeyCaptor.getValue();
        assertTrue(privKey.contains("-----BEGIN RSA PRIVATE KEY-----"));
        assertTrue(privKey.contains("-----END RSA PRIVATE KEY-----"));
    }

    @Test
    public void bothKeysAlreadyExist() {
        // Mock S3 client. Both keys exist.
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(true);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                true);

        // execute
        svc.createCmsKeyPair(STUDY_ID);

        // We never upload to S3.
        verify(svc, never()).s3Put(any(), any(), any());
    }
}
