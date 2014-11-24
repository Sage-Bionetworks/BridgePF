package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.BcCertificateFactory;
import org.sagebionetworks.bridge.crypto.CertificateFactory;
import org.sagebionetworks.bridge.crypto.KeyPairFactory;
import org.sagebionetworks.bridge.crypto.PemUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class UploadCertificateServiceImpl implements UploadCertificateService {

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();
    private static final String PRIVATE_KEY_BUCKET = CONFIG.getProperty("");
    private static final String CERT_BUCKET = CONFIG.getProperty("");

    private final CertificateFactory certificateFactory;

    public UploadCertificateServiceImpl() {
        certificateFactory = new BcCertificateFactory();
    }

    private AmazonS3 s3CmsClient;
    public void setS3CmsClient(AmazonS3 s3CmsClient) {
        this.s3CmsClient = s3CmsClient;
    }

    @Override
    public void createCmsKeyPair(String studyIdentifier) {
        checkNotNull(studyIdentifier);
        final KeyPair keyPair = KeyPairFactory.newRsa2048();
        final String studyFqdn = CONFIG.getStudyHostname(studyIdentifier);
        final X509Certificate cert = certificateFactory.newCertificate(keyPair, studyFqdn);
        final String name = studyFqdn + ".pem";
        try {
            s3Put(PRIVATE_KEY_BUCKET, name,  PemUtils.toPem(keyPair.getPrivate()));
            s3Put(CERT_BUCKET, name, PemUtils.toPem(cert));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void s3Put(String bucket, String name, String pem) {
        byte[] bytes = pem.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            s3CmsClient.putObject(bucket, name, bais, metadata);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
