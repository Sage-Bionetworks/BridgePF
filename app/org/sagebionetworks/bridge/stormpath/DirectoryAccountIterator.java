package org.sagebionetworks.bridge.stormpath;

import java.util.Iterator;
import java.util.SortedMap;

import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.impl.account.DefaultAccountCriteria;

public class DirectoryAccountIterator implements Iterator<Account> {
    
    // 100 is the maximum allowed page size according to the Stormpath API docs.
    private static final int DEFAULT_PAGE_SIZE = 100;
    
    private Directory directory;
    private StudyIdentifier studyIdentifier;
    private SortedMap<Integer,Encryptor> encryptors;
    private int pageOffset = 0;
    private Iterator<com.stormpath.sdk.account.Account> iterator;
    
    DirectoryAccountIterator(Directory directory, StudyIdentifier studyIdentifier, SortedMap<Integer,Encryptor> encryptors) {
        this.directory = directory;
        this.studyIdentifier = studyIdentifier;
        this.encryptors = encryptors;
        this.iterator = retrievePage();
    }

    @Override
    public boolean hasNext() {
        if (iterator.hasNext()) {
            return true;
        }
        iterator = retrievePage(); // check for a next page
        return iterator.hasNext();
    }

    @Override
    public Account next() {
        return new StormpathAccount(studyIdentifier, iterator.next(), encryptors);
    }

    private Iterator<com.stormpath.sdk.account.Account> retrievePage() {
        AccountCriteria criteria = new DefaultAccountCriteria();
        criteria.offsetBy(pageOffset);
        criteria.limitTo(DEFAULT_PAGE_SIZE);
        criteria.withCustomData();

        pageOffset += DEFAULT_PAGE_SIZE; // increment AFTER

        return directory.getAccounts(criteria).iterator();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove accounts through the account iterator.");
    }

}
