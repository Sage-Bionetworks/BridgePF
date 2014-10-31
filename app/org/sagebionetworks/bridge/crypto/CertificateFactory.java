package org.sagebionetworks.bridge.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public interface CertificateFactory {

    /**
     * Creates a self-signed X509 certificate.
     */
    X509Certificate newCertificate(KeyPair keyPair);

    /**
     * Creates an X509 certificate chained to a certifying authority (ca) certificate.
     *
     * @param publicKey   The public key associated with this certificate
     * @param caCert      Issuer's certificate
     * @param privateKey  Issuer's private key
     * @return
     */
    X509Certificate newCertificate(PublicKey publicKey, X509Certificate caCert, PrivateKey privateKey);
}
