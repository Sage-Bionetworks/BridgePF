package org.sagebionetworks.bridge.okta;

import java.io.IOException;
import java.util.Iterator;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.okta.sdk.clients.UserApiClient;
import com.okta.sdk.framework.PagedResults;
import com.okta.sdk.models.users.User;

public class OktaAccountIterator implements Iterator<AccountSummary> {
    
    private static final int LIMIT = 100;
    private final UserApiClient userApiClient;
    private final StudyIdentifier studyId;
    private PagedResults<User> page;
    private Iterator<User> iterator;
    
    public OktaAccountIterator(StudyIdentifier studyId, UserApiClient userApiClient) {
        this.studyId = studyId;
        this.userApiClient = userApiClient;
        try {
            page = userApiClient.getUsersPagedResultsWithLimit(LIMIT);
            iterator = page.getResult().iterator();
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public boolean hasNext() {
        try {
            if (!iterator.hasNext() && !page.isLastPage()) {
                page = userApiClient.getUsersPagedResultsAfterCursorWithLimit(page.getNextUrl(), LIMIT);
                iterator = page.getResult().iterator();
            }
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        return iterator.hasNext();
    }

    @Override
    public AccountSummary next() {
        User acct = iterator.next();
        if (acct != null) {
            return AccountSummary.create(studyId, acct);
        }
        return null;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
