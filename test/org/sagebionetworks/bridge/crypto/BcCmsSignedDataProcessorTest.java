package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.security.cert.Certificate;
import java.security.KeyPair;
import java.util.Collection;
import java.util.Iterator;

import org.apache.shiro.codec.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.junit.Test;

public class BcCmsSignedDataProcessorTest {

    @Test
    public void test() throws Exception {

        CertificateFactory certFactory = new BcCertificateFactory();
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        Certificate cert = certFactory.newCertificate(keyPair);
        Certificate[] certChain = new Certificate[]{cert};
        CmsSignedDataProcessor processor = new BcCmsSignedDataProcessor(certChain, keyPair.getPrivate());

        String text = "some test text";
        String base64Encoded = Base64.encodeToString(text.getBytes());

        String signed = processor.sign(base64Encoded);
        assertNotNull(signed);

        CMSSignedData signedData = new CMSSignedData(Base64.decode(signed));
        Store certStore = signedData.getCertificates();
        SignerInformationStore signerStore = signedData.getSignerInfos();
        Collection<?> signers = signerStore.getSigners();
        for (Object obj : signers)
        {
            SignerInformation signer = (SignerInformation)obj;
            Collection<?> certCollection = certStore.getMatches(signer.getSID());
            Iterator<?> certIt = certCollection.iterator();
            X509CertificateHolder certHolder = (X509CertificateHolder)certIt.next();
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BcCmsConstants.PROVIDER).build(certHolder))) {
                fail();
            }   
        }
    }
}
