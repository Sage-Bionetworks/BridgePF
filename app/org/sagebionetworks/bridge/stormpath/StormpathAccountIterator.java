package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.SortedMap;

import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;

public final class StormpathAccountIterator implements Iterator<Account> {

    private final Study study;
    private final SortedMap<Integer, BridgeEncryptor> encryptors;
    private final Iterator<com.stormpath.sdk.account.Account> iterator;
    
    public StormpathAccountIterator(Study study, SortedMap<Integer, BridgeEncryptor> encryptors, Iterator<com.stormpath.sdk.account.Account> iterator) {
        checkNotNull(study);
        checkNotNull(encryptors);
        checkNotNull(iterator);
        
        this.study = study;
        this.encryptors = encryptors;
        this.iterator = iterator;
    }
    
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Account next() {
        com.stormpath.sdk.account.Account acct = iterator.next();
        if (acct != null) {
            return new StormpathAccount(study, acct, encryptors);
        }
        return null;
    }

    @Override
    public void remove() {
        iterator.remove();
    }

}
