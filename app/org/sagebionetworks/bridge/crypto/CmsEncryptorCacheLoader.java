package org.sagebionetworks.bridge.crypto;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import com.google.common.cache.CacheLoader;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.s3.S3Helper;

/**
 * This is the cache loader that supports loading CMS encryptors on demand, keyed by the study ID. If the study's
 * encryptor is already in the cache, this returns that encryptor. If it isn't, this study will pull the PEM fils for
 * the cert and private key from the configured S3 bucket and construct an encryptor using those encryption materials.
 */
@Component
public class CmsEncryptorCacheLoader extends CacheLoader<String, CmsEncryptor> {
    private static final String PEM_FILENAME_FORMAT = "%s.pem";

    // These constants are package-scoped to make them accessible to unit tests.
    /* package-scoped */ static final String CERT_BUCKET = BridgeConfigFactory.getConfig().getProperty(
            "upload.cms.cert.bucket");
    /* package-scroped */ static final String PRIV_KEY_BUCKET = BridgeConfigFactory.getConfig().getProperty(
            "upload.cms.priv.bucket");

    private S3Helper s3CmsHelper;

    /** S3 helper, configured by Spring. */
    @Resource(name = "s3CmsHelper")
    public void setS3CmsHelper(S3Helper s3CmsHelper) {
        this.s3CmsHelper = s3CmsHelper;
    }

    /** {@inheritDoc} */
    @Override
    public CmsEncryptor load(@Nonnull String studyId) throws CertificateEncodingException, IOException {
        String pemFileName = String.format(PEM_FILENAME_FORMAT, studyId);

        // download certificate
        String certPem = s3CmsHelper.readS3FileAsString(CERT_BUCKET, pemFileName);
        X509Certificate cert = PemUtils.loadCertificateFromPem(certPem);

        // download private key
        String privKeyPem = s3CmsHelper.readS3FileAsString(PRIV_KEY_BUCKET, pemFileName);
        PrivateKey privKey = PemUtils.loadPrivateKeyFromPem(privKeyPem);

        return new BcCmsEncryptor(cert, privKey);
    }
}
