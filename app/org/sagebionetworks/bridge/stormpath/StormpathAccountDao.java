package org.sagebionetworks.bridge.stormpath;

import java.util.Iterator;

import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;

import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

public class StormpathAccountDao implements AccountDao {

    private Client client;
    private AesGcmEncryptor healthCodeEncryptor;
    private AccountEncryptionService accountEncryptionService;
    
    void setStormpathClient(Client client) {
        this.client = client;
    }
    
    void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }
    
    void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    @Override
    public Iterator<Account> getAllAccounts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Iterator<Account> getStudyAccounts(Study study) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Account getAccount(Study study, String email) {
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        
        AccountList accounts = directory.getAccounts(Accounts.where(Accounts.email().eqIgnoreCase(email)));
        if (!accounts.iterator().hasNext()) {
            throw new EntityNotFoundException(Account.class);
        }
        com.stormpath.sdk.account.Account acct = accounts.iterator().next();
        
        Account account = new StormpathAccount(acct, healthCodeEncryptor, accountEncryptionService);
        
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Account createAccount(Study study, Account account) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateAcount(Study study, Account account) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void deleteAccount(Study study, String email) {
        // TODO Auto-generated method stub
        
    }

}
