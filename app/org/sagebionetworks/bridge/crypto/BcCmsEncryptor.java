package org.sagebionetworks.bridge.crypto;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.apache.shiro.codec.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.KeyTransRecipientId;
import org.bouncycastle.cms.Recipient;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInfoGenerator;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputEncryptor;

public final class BcCmsEncryptor implements CmsEncryptor {

    public BcCmsEncryptor(X509Certificate cert, PrivateKey privateKey) throws CertificateEncodingException {
        if (cert == null) {
            throw new IllegalArgumentException("Cert cannot be null.");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("Private key cannot be null.");
        }
        Security.addProvider(new BouncyCastleProvider());
        generator = new CMSEnvelopedDataGenerator();
        RecipientInfoGenerator recipientInfoGenerator =
                new JceKeyTransRecipientInfoGenerator(cert).setProvider(BcCmsConstants.PROVIDER);
        generator.addRecipientInfoGenerator(recipientInfoGenerator);
        this.cert = cert;
        this.privateKey = privateKey;
    }

    @Override
    public String encrypt(String base64Encoded) throws CMSException, IOException {
        CMSTypedData cmsData = new CMSProcessableByteArray(Base64.decode(base64Encoded));
        OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(BcCmsConstants.ENCRYPTOR_ALGO)
                .setProvider(BcCmsConstants.PROVIDER).build();
        CMSEnvelopedData envelopedData = generator.generate(cmsData, encryptor);
        return Base64.encodeToString(envelopedData.getEncoded());
    }

    @Override
    public String decrypt(String base64Encoded) throws CMSException, CertificateEncodingException, IOException {
        CMSEnvelopedData envelopedData = new CMSEnvelopedData(Base64.decode(base64Encoded));
        X509CertificateHolder certHolder = new X509CertificateHolder(cert.getEncoded());
        RecipientId recipientId = new KeyTransRecipientId(certHolder.getIssuer(), certHolder.getSerialNumber());
        RecipientInformation recInfo = envelopedData.getRecipientInfos().get(recipientId);
        Recipient recipient = new JceKeyTransEnvelopedRecipient(privateKey);
        byte[] bytes = recInfo.getContent(recipient);
        return Base64.encodeToString(bytes);
    }

    private final CMSEnvelopedDataGenerator generator;
    private final X509Certificate cert;
    private final PrivateKey privateKey;
}
