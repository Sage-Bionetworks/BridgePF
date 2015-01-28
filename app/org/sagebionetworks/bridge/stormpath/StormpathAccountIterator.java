package org.sagebionetworks.bridge.stormpath;

import java.util.Iterator;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.impl.account.DefaultAccountCriteria;

/**
 * Iterates through Stormpath accounts page by page.
 */
public class StormpathAccountIterator extends PageIterator<Account> {

    private static final int DEFAULT_PAGE_SIZE = 50;
    
    private final Application app;
    private final int pageSize;

    public StormpathAccountIterator(Application app) {
        this(app, DEFAULT_PAGE_SIZE);
    }
    
    public StormpathAccountIterator(Application app, int pageSize) {
        this.app = app;
        this.pageSize = pageSize;
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    @Override
    public Iterator<Account> nextPage() {
        AccountCriteria criteria = new DefaultAccountCriteria();
        criteria.offsetBy(pageStart());
        criteria.limitTo(pageSize());
        criteria.withCustomData();
        AccountList list = app.getAccounts(criteria);
        return list.iterator();
    }
}
