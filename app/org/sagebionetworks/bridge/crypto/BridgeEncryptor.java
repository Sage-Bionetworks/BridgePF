package org.sagebionetworks.bridge.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

public class BridgeEncryptor implements Encryptor {

    private static final Integer VERSION = Integer.valueOf(2);

    private final Encryptor encryptor;

    public BridgeEncryptor(final Encryptor encryptor) {
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

    public Integer getVersion() {
        return VERSION;
    }
}
