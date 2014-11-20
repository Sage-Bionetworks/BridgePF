package org.sagebionetworks.bridge.crypto;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;

import org.bouncycastle.cms.CMSException;

/**
 * Does CMS encryption.
 */
public interface CmsEncryptor {

    byte[] encrypt(byte[] bytes) throws CMSException, IOException;

    byte[] decrypt(byte[] bytes) throws CMSException, CertificateEncodingException, IOException;
}
