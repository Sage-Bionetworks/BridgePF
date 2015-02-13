package org.sagebionetworks.bridge.dao;

public interface AccountDao {

    // TODO: Update to StudyIdentifier when that exists
    
    public Account getAccount(String studyIdentifier, String email);
    
    public Account createAccount(String studyIdentifier, Account account);
    
    public void updateAcount(String studyIdentifier, Account account);
    
    public void deleteAccount(String studyIdentifier, String email);
    
}
