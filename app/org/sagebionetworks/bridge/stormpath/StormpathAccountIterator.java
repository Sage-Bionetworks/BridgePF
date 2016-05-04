package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public final class StormpathAccountIterator implements Iterator<AccountSummary> {

    private final StudyIdentifier studyId;
    private final Iterator<com.stormpath.sdk.account.Account> iterator;
    
    public StormpathAccountIterator(StudyIdentifier studyId, Iterator<com.stormpath.sdk.account.Account> iterator) {
        checkNotNull(iterator);
        
        this.studyId = studyId;
        this.iterator = iterator;
    }
    
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public AccountSummary next() {
        com.stormpath.sdk.account.Account acct = iterator.next();
        if (acct != null) {
            return AccountSummary.create(studyId, acct);
        }
        return null;
    }

    @Override
    public void remove() {
        iterator.remove();
    }

}
