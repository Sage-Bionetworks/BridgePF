package org.sagebionetworks.bridge.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

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

    private final CMSEnvelopedDataGenerator generator;
    private final X509Certificate cert;
    private final PrivateKey privateKey;

    public BcCmsEncryptor(X509Certificate cert, PrivateKey privateKey) throws CertificateEncodingException {
        checkNotNull(cert);
        checkNotNull(privateKey);
        Security.addProvider(new BouncyCastleProvider());
        generator = new CMSEnvelopedDataGenerator();
        RecipientInfoGenerator recipientInfoGenerator =
                new JceKeyTransRecipientInfoGenerator(cert).setProvider(BcCmsConstants.PROVIDER);
        generator.addRecipientInfoGenerator(recipientInfoGenerator);
        this.cert = cert;
        this.privateKey = privateKey;
    }

    @Override
    public byte[] encrypt(byte[] bytes) throws CMSException, IOException {
        checkNotNull(bytes);
        CMSTypedData cmsData = new CMSProcessableByteArray(bytes);
        OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(BcCmsConstants.ENCRYPTOR_ALGO)
                .setProvider(BcCmsConstants.PROVIDER).build();
        CMSEnvelopedData envelopedData = generator.generate(cmsData, encryptor);
        byte[] encrypted = envelopedData.getEncoded();
        return encrypted;
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws CMSException, CertificateEncodingException, IOException {
        checkNotNull(bytes);
        CMSEnvelopedData envelopedData = new CMSEnvelopedData(bytes);
        X509CertificateHolder certHolder = new X509CertificateHolder(cert.getEncoded());
        RecipientId recipientId = new KeyTransRecipientId(certHolder.getIssuer(), certHolder.getSerialNumber());
        RecipientInformation recInfo = envelopedData.getRecipientInfos().get(recipientId);
        Recipient recipient = new JceKeyTransEnvelopedRecipient(privateKey);
        byte[] decrypted = recInfo.getContent(recipient);
        return decrypted;
    }
}
