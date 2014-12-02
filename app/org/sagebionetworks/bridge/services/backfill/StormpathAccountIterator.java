package org.sagebionetworks.bridge.services.backfill;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.impl.account.DefaultAccountCriteria;

public class StormpathAccountIterator implements Iterator<List<Account>> {

    private static final int PAGE_SIZE = 50;
    private final Application app;
    private int offset;
    private boolean hasNext;

    StormpathAccountIterator(Application app) {
        this.app = app;
        offset = 0;
        hasNext = true;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public List<Account> next() {
        AccountCriteria criteria = new DefaultAccountCriteria();
        criteria.offsetBy(offset);
        criteria.limitTo(PAGE_SIZE);
        AccountList list = app.getAccounts(criteria);
        Iterator<Account> iterator = list.iterator();
        List<Account> accountList = new ArrayList<Account>();
        while (iterator.hasNext()) {
            accountList.add(iterator.next());
        }
        hasNext = accountList.size() == PAGE_SIZE;
        offset = offset + PAGE_SIZE;
        return accountList;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
