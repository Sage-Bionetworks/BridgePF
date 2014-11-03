package org.sagebionetworks.bridge.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class BcCertificateFactory implements CertificateFactory {

    public BcCertificateFactory() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public X509Certificate newCertificate(KeyPair keyPair) {

        final X500NameBuilder issuer = new X500NameBuilder(BCStyle.INSTANCE);
        // Common name
        issuer.addRDN(BCStyle.CN, "Sage Bridge Self-Signed");
        // Organizational unit name
        issuer.addRDN(BCStyle.OU, "Bridge Project");
        // Organization name
        issuer.addRDN(BCStyle.O, "Sage Bionetworks");
        // Country code
        issuer.addRDN(BCStyle.C, "US");

        final X500NameBuilder subject = new X500NameBuilder(BCStyle.INSTANCE);
        subject.addRDN(BCStyle.CN, "Sage Bridge Self-Signed");

        // TODO: Serial
        BigInteger serial = BigInteger.ONE;

        DateTime now = DateTime.now(DateTimeZone.UTC);
        Date notBefore = now.minusDays(1).toDate();
        Date notAfter = now.plusDays(365).toDate();

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer.build(), serial, notBefore, notAfter, subject.build(), keyPair.getPublic());
        // TODO: Add extensions here to the cert builder

        try {
            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(ALGORITHM).setProvider(PROVIDER);
            ContentSigner consentSigner = signerBuilder.build(keyPair.getPrivate());
            X509CertificateHolder certHolder = certBuilder.build(consentSigner);
            return new JcaX509CertificateConverter().setProvider(PROVIDER).getCertificate(certHolder);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public X509Certificate newCertificate(PublicKey publicKey, X509Certificate caCert, PrivateKey privateKey) {

        BigInteger serial = BigInteger.ONE;
        DateTime now = DateTime.now(DateTimeZone.UTC);
        Date notBefore = now.minusDays(1).toDate();
        Date notAfter = now.plusDays(365).toDate();
        final X500NameBuilder subject = (new X500NameBuilder(BCStyle.INSTANCE)).addRDN(BCStyle.CN, "Sage Bridge Self-Signed");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caCert, serial, notBefore, notAfter, subject.build(), publicKey);
        // TODO: Add extensions here to the builder

        try {
            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(ALGORITHM).setProvider(PROVIDER);
            ContentSigner consentSigner = signerBuilder.build(privateKey);
            X509CertificateHolder certHolder = certBuilder.build(consentSigner);
            return new JcaX509CertificateConverter().setProvider(PROVIDER).getCertificate(certHolder);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String ALGORITHM = "SHA256withRSA";
    private static final String PROVIDER = "BC";
}
