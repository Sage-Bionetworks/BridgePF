package org.sagebionetworks.bridge.stormpath;

import java.util.Iterator;

import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.AccountStoreMappingCriteria;
import com.stormpath.sdk.application.AccountStoreMappingList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.impl.application.DefaultAccountStoreMappingCriteria;

public class AccountStoreMappingPageIterator extends PageIterator<AccountStoreMapping> {

    private static final int PAGE_SIZE = 50;
    private final Application app;

    public AccountStoreMappingPageIterator(Application app) {
        this.app = app;
    }
    
    @Override
    public int pageSize() {
        return PAGE_SIZE;
    }

    @Override
    public Iterator<AccountStoreMapping> nextPage() {
        AccountStoreMappingCriteria criteria = new DefaultAccountStoreMappingCriteria();
        criteria.offsetBy(pageStart());
        criteria.limitTo(pageSize());
        AccountStoreMappingList list = app.getAccountStoreMappings(criteria);
        return list.iterator();
    }

}