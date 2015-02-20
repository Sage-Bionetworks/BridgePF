package org.sagebionetworks.bridge.crypto;

/**
 * The interface of an encryption service that will be utilized by Accounts to encrypt/decrypt
 * personally identifying health information stored with the account.
 *
 */
public interface Encryptor {
    public Integer getVersion();
    public String encrypt(String text);
    public String decrypt(String text);
}
