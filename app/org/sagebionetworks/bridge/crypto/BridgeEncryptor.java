package org.sagebionetworks.bridge.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.salt.RandomSaltGenerator;

public class BridgeEncryptor implements StringEncryptor {

    private final StringEncryptor encryptor;

    public BridgeEncryptor(String password) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setProvider(new BouncyCastleProvider());
        encryptor.setAlgorithm("PBEWITHSHAAND256BITAES-CBC-BC");
        encryptor.setPassword(password);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        this.encryptor = encryptor;
    }

    public String encrypt(String string) {
        return encryptor.encrypt(string);
    }

    public String decrypt(String string) {
        return encryptor.decrypt(string);
    }
}
