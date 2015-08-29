package org.sagebionetworks.bridge.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

public class BridgeAesGcmEncryptor implements BridgeEncryptor {

    private static final Integer VERSION = Integer.valueOf(2);

    private final AesGcmEncryptor encryptor;

    public BridgeAesGcmEncryptor(final AesGcmEncryptor encryptor) {
        checkNotNull(encryptor);
        this.encryptor = encryptor;
    }

    @Override
    public String decrypt(String text) {
        return encryptor.decrypt(text);
    }

    @Override
    public String encrypt(String text) {
        return encryptor.encrypt(text);
    }

    @Override
    public Integer getVersion() {
        return VERSION;
    }
}
