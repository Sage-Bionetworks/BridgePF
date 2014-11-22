package org.sagebionetworks.bridge.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class BcCertificateFactory implements CertificateFactory {

    private static final BridgeConfig config = BridgeConfigFactory.getConfig();

    public BcCertificateFactory() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public X509Certificate newCertificate(KeyPair keyPair, String studyFqdn) {
        checkNotNull(keyPair);
        checkNotNull(studyFqdn);
        CertificateInfo certInfo = new CertificateInfo()
                .withCountry(config.getProperty("upload.cms.certificate.country"))
                .withState(config.getProperty("upload.cms.certificate.state"))
                .withCity(config.getProperty("upload.cms.certificate.city"))
                .withOrganization(config.getProperty("upload.cms.certificate.organization"))
                .withTeam(config.getProperty("upload.cms.certificate.team"))
                .withEmail(config.getProperty("upload.cms.certificate.email"))
                .withFqdn(studyFqdn);
        return newCertificate(keyPair, certInfo);
    }

    @Override
    public X509Certificate newCertificate(KeyPair keyPair, CertificateInfo certInfo) {

        checkNotNull(keyPair);
        checkNotNull(certInfo);

        // Create a cert builder
        DateTime now = DateTime.now(DateTimeZone.UTC);
        final Date notBefore = now.minusDays(2).toDate();
        final Date notAfter = now.plusDays(9999).toDate();
        final X500Name issuer = getIssuer(certInfo);
        final X500Name subject = getSubject(certInfo); 
        final BigInteger serial = new BigInteger(Long.toString(System.nanoTime()));
        final PublicKey publicKey = keyPair.getPublic();
        final X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, publicKey);

        // Add extensions
        try {
            // Certificate Authority: YES
            certBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));
            final X509ExtensionUtils extUtils = new BcX509ExtensionUtils();
            final SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(
                    AlgorithmIdentifier.getInstance(BcCmsConstants.KEY_PAIR_ALGO_ID), publicKey.getEncoded());
            GeneralNames names = new GeneralNames(new GeneralName(issuer));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(publicKeyInfo, names, serial));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(publicKeyInfo));
        } catch (CertIOException e) {
            throw new RuntimeException(e);
        }

        // Build a cert
        try {
            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(BcCmsConstants.SIGNER_ALGO).setProvider(BcCmsConstants.PROVIDER);
            ContentSigner consentSigner = signerBuilder.build(keyPair.getPrivate());
            X509CertificateHolder certHolder = certBuilder.build(consentSigner);
            return new JcaX509CertificateConverter().setProvider(BcCmsConstants.PROVIDER).getCertificate(certHolder);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public X509Certificate newCertificate(PublicKey publicKey, X509Certificate caCert, PrivateKey caPrivateKey) {

        BigInteger serial = BigInteger.ONE;
        DateTime now = DateTime.now(DateTimeZone.UTC);
        Date notBefore = now.minusDays(1).toDate();
        Date notAfter = now.plusDays(365).toDate();
        final X500NameBuilder subject = (new X500NameBuilder(BCStyle.INSTANCE)).addRDN(BCStyle.CN, "Sage Bridge Self-Signed");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caCert, serial, notBefore, notAfter, subject.build(), publicKey);

        try {
            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(BcCmsConstants.SIGNER_ALGO).setProvider(BcCmsConstants.PROVIDER);
            ContentSigner consentSigner = signerBuilder.build(caPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(consentSigner);
            return new JcaX509CertificateConverter().setProvider(BcCmsConstants.PROVIDER).getCertificate(certHolder);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private X500Name getSubject(CertificateInfo certInfo) {
        X500NameBuilder issuer = new X500NameBuilder(BCStyle.INSTANCE);
        issuer.addRDN(BCStyle.C, certInfo.getCountry());
        issuer.addRDN(BCStyle.ST, certInfo.getState());
        issuer.addRDN(BCStyle.L, certInfo.getCity());
        issuer.addRDN(BCStyle.O, certInfo.getOrganization());
        issuer.addRDN(BCStyle.OU, certInfo.getTeam());
        issuer.addRDN(BCStyle.EmailAddress, certInfo.getEmail());
        issuer.addRDN(BCStyle.CN, certInfo.getFqdn());
        return issuer.build();
    }

    private X500Name getIssuer(CertificateInfo certInfo) {
        X500NameBuilder issuer = new X500NameBuilder(BCStyle.INSTANCE);
        issuer.addRDN(BCStyle.C, certInfo.getCountry());
        issuer.addRDN(BCStyle.ST, certInfo.getState());
        issuer.addRDN(BCStyle.L, certInfo.getCity());
        issuer.addRDN(BCStyle.O, certInfo.getOrganization());
        issuer.addRDN(BCStyle.OU, certInfo.getTeam());
        issuer.addRDN(BCStyle.EmailAddress, certInfo.getEmail());
        issuer.addRDN(BCStyle.CN, certInfo.getFqdn());
        return issuer.build();
    }
}
