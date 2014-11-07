package org.sagebionetworks.bridge.crypto;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;

public interface BcCmsConstants {

    String PROVIDER = "BC";
    String SIGNER_ALGO = "SHA256withRSA";
    ASN1ObjectIdentifier ENCRYPTOR_ALGO = CMSAlgorithm.AES256_CBC;
}
