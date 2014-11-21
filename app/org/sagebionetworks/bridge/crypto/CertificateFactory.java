package org.sagebionetworks.bridge.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public interface CertificateFactory {

    /**
     * Creates a self-signed X509 certificate for a specific study with default information.
     */
    X509Certificate newCertificate(KeyPair keyPair, String studyKey);

    /**
     * Creates a self-signed X509 certificate with the supplied information.
     */
    X509Certificate newCertificate(KeyPair keyPair, CertificateInfo certInfo);

    /**
     * Creates an X509 certificate chained to a certifying authority (ca) certificate.
     *
     * @param publicKey   The public key associated with this certificate
     * @param caCert      Issuer's certificate
     * @param privateKey  Issuer's private key
     */
    X509Certificate newCertificate(PublicKey publicKey, X509Certificate caCert, PrivateKey caPrivateKey);
}
