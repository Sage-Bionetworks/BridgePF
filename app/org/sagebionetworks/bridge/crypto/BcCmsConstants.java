package org.sagebionetworks.bridge.crypto;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSAlgorithm;

final class BcCmsConstants {

    final static String PROVIDER = "BC";
    final static String KEY_PAIR_ALGO = "RSA";
    final static String SIGNER_ALGO = "SHA1withRSA";
    final static ASN1ObjectIdentifier KEY_PAIR_ALGO_ID = PKCSObjectIdentifiers.rsaEncryption;
    final static ASN1ObjectIdentifier ENCRYPTOR_ALGO_ID = CMSAlgorithm.AES256_CBC;

    private BcCmsConstants() {}
}
