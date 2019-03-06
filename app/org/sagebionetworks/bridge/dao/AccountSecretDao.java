package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;

/**
 * DAO to manage the creation and verification of passwords and reauthentication tokens. 
 * (Note though that passwords are currently still stored in the Accounts table.)
 */
public interface AccountSecretDao {
    /**
     * Add a secret to the set of secrets. 
     */
    void createSecret(AccountSecretType type, String accountId, String plaintext);
    
    /**
     * Retrieve N secret records (indicated by rotations), and compare the provided secret 
     * against all of those secrets looking for a match. Return the record if a match is found,
     * or null otherwise.
     */
    Optional<AccountSecret> verifySecret(AccountSecretType type, String accountId, String plaintext, int rotations);
    
    /**
     * Delete all secrets for the indicated user, of the indicated type.
     */
    void removeSecrets(AccountSecretType type, String accountId);
}
