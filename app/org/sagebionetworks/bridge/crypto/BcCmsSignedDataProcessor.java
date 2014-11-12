package org.sagebionetworks.bridge.crypto;

import static org.sagebionetworks.bridge.crypto.BcCmsConstants.PROVIDER;
import static org.sagebionetworks.bridge.crypto.BcCmsConstants.SIGNER_ALGO;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.shiro.codec.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

public class BcCmsSignedDataProcessor implements CmsSignedDataProcessor {

    public BcCmsSignedDataProcessor(Certificate[] certChain, PrivateKey privateKey)
            throws OperatorCreationException, CertificateEncodingException, IOException, CMSException {
        Security.addProvider(new BouncyCastleProvider());
        DigestCalculatorProvider  digestCalculatorProvider = new JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER).build();
        ContentSigner signer = new JcaContentSignerBuilder(SIGNER_ALGO).setProvider(PROVIDER).build(privateKey);
        SignerInfoGenerator signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
                .build(signer, new X509CertificateHolder(certChain[0].getEncoded()));
        generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(signerInfoGenerator);
        List<Certificate> certs = new ArrayList<Certificate>();
        for (Certificate cert : certChain) {
            certs.add(cert);
        }
        Store certStore = new JcaCertStore(certs);
        generator.addCertificates(certStore);
    }

    @Override
    public String sign(String base64Encoded) throws IOException, CMSException {
        CMSTypedData data = new CMSProcessableByteArray(Base64.decode(base64Encoded));
        CMSSignedData signedData = generator.generate(data, true);
        return Base64.encodeToString(signedData.getEncoded());
    }

    private final CMSSignedDataGenerator generator;
}
