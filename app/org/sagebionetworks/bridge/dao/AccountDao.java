package org.sagebionetworks.bridge.dao;

import java.util.Iterator;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;

public interface AccountDao {

    public Iterator<Account> getAllAccounts();
    
    public Iterator<Account> getStudyAccounts(Study study);
    
    public Account getAccount(Study study, String email);
    
    public Account createAccount(Study study, Account account);
    
    public void updateAcount(Study study, Account account);
    
    public void deleteAccount(Study study, String email);
    
}
