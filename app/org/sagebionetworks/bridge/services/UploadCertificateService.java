package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.BcCertificateFactory;
import org.sagebionetworks.bridge.crypto.CertificateFactory;
import org.sagebionetworks.bridge.crypto.CertificateInfo;
import org.sagebionetworks.bridge.crypto.KeyPairFactory;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.springframework.stereotype.Component;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;

@Component("uploadCertificateService")
public class UploadCertificateService {

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();

    // S3 buckets for priv keys and certs. Package-scoped to facilitate unit tests.
    static final String PRIVATE_KEY_BUCKET = CONFIG.getProperty("upload.cms.priv.bucket");
    static final String CERT_BUCKET = CONFIG.getProperty("upload.cms.cert.bucket");

    private final CertificateFactory certificateFactory;
    private AmazonS3 s3CmsClient;
    private S3Helper s3CmsHelper;

    public UploadCertificateService() {
        certificateFactory = new BcCertificateFactory();
    }

    @Resource(name = "s3CmsClient")
    public final void setS3CmsClient(AmazonS3 s3CmsClient) {
        this.s3CmsClient = s3CmsClient;
    }

    @Resource(name = "s3CmsHelper")
    public final void setS3CmsHelper(S3Helper s3CmsHelper) {
        this.s3CmsHelper = s3CmsHelper;
    }

    /**
     * Creates a CMS key pair for a particular study and save it in permanent storage.
     */
    public void createCmsKeyPair(final StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier);
        String pemFilename = getPemFilename(studyIdentifier);
        if (!s3CmsClient.doesObjectExist(PRIVATE_KEY_BUCKET, pemFilename) ||
                !s3CmsClient.doesObjectExist(CERT_BUCKET, pemFilename)) {
            final KeyPair keyPair = KeyPairFactory.newRsa2048();
            final CertificateInfo certInfo = new CertificateInfo.Builder()
                    .country(CONFIG.getProperty("upload.cms.certificate.country"))
                    .state(CONFIG.getProperty("upload.cms.certificate.state"))
                    .city(CONFIG.getProperty("upload.cms.certificate.city"))
                    .organization(CONFIG.getProperty("upload.cms.certificate.organization"))
                    .team(CONFIG.getProperty("upload.cms.certificate.team"))
                    .email(CONFIG.getProperty("upload.cms.certificate.email")).fqdn(CONFIG.getWebservicesURL()).build();
            final X509Certificate cert = certificateFactory.newCertificate(keyPair, certInfo);
            try {
                s3Put(PRIVATE_KEY_BUCKET, pemFilename, PemUtils.toPem(keyPair.getPrivate()));
                s3Put(CERT_BUCKET, pemFilename, PemUtils.toPem(cert));
            } catch (CertificateEncodingException e) {
                throw new BridgeServiceException(e);
            }
        }
    }

    // Helper function to get the PEM filename. Package-scoped to facilitate unit tests.
    static String getPemFilename(StudyIdentifier studyId) {
        return studyId.getIdentifier() + ".pem";
    }

    /**
     * Get the PEM file for the public key of the CMS key pair. Study developers need 
     * access to this certificate to encrypt data they send to us.
     */
    public String getPublicKeyAsPem(StudyIdentifier studyIdentifier) {
        try {
            return s3CmsHelper.readS3FileAsString(CERT_BUCKET, getPemFilename(studyIdentifier));
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }

    // package-scoped to facilitate unit tests
    void s3Put(String bucket, String name, String pem) {
        byte[] bytes = pem.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            s3CmsClient.putObject(bucket, name, bais, metadata);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
}
