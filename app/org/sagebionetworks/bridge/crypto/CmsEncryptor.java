package org.sagebionetworks.bridge.crypto;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;

import org.bouncycastle.cms.CMSException;

/**
 * Does CMS encryption.
 */
public interface CmsEncryptor {

    /**
     * @param base64Encoded Base64-encoded string to be encrypted
     * @return Encrypted string, Base64-encoded
     */
    String encrypt(String base64Encoded) throws CMSException, IOException;

    /**
     * @param base64Encoded Base64-encoded string to be decrypted
     * @return Decrypted string, Base64-encoded
     */
    String decrypt(String base64Encoded) throws CMSException, CertificateEncodingException, IOException;
}
