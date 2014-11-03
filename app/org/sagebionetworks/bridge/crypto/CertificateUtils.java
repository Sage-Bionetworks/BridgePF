package org.sagebionetworks.bridge.crypto;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Base64;

public class CertificateUtils {

    public static String toPem(X509Certificate cert) throws CertificateEncodingException {
        if (cert == null) {
            throw new IllegalArgumentException("Cert cannot be null.");
        }
        Base64 encoder = new Base64(LINE_LENGTH);
        return BEGIN_CERT + "\n" + encoder.encodeToString(cert.getEncoded()) + END_CERT;
    }

    private static final int LINE_LENGTH = 64;
    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERT = "-----END CERTIFICATE-----";
}
