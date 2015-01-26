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

    private static final int PAGE_SIZE = 50;
    private final Application app;

    public StormpathAccountIterator(Application app) {
        this.app = app;
    }

    @Override
    public int pageSize() {
        return PAGE_SIZE;
    }

    @Override
    public Iterator<Account> nextPage() {
        AccountCriteria criteria = new DefaultAccountCriteria();
        criteria.offsetBy(pageStart());
        criteria.limitTo(pageSize());
        AccountList list = app.getAccounts(criteria);
        return list.iterator();
    }
}
