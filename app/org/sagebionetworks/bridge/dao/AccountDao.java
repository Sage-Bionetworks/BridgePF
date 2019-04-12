package org.sagebionetworks.bridge.dao;

import java.util.function.Consumer;

import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

/**
 * DAO to retrieve personally identifiable account information, including authentication 
 * credentials. To work with users, use the ParticipantService, which orchestrates calls 
 * to the AccountDao in order to reduce the number of times we make calls to our external 
 * authentication service.
 */
public interface AccountDao {
    
    int MIGRATION_VERSION = 1;
    
    /**
     * Set the verified flag for the channel (email or phone) to true, and enable the account (if needed).
     */
    void verifyChannel(AuthenticationService.ChannelType channelType, Account account);
    
    /**
     * Call to change a password, possibly verifying the channel used to reset the password. The channel 
     * type (which is optional, and can be null) is the channel that has been verified through the act 
     * of successfully resetting the password (sometimes there is no channel that is verified). 
     */
    void changePassword(Account account, ChannelType channelType, String newPassword);
    
    /**
     * Authenticate a user with the supplied credentials, returning that user's account record
     * if successful. 
     */
    Account authenticate(Study study, SignIn signIn);

    /**
     * Re-acquire a valid session using a special token passed back on an
     * authenticate request. Allows the client to re-authenticate without prompting
     * for a password.
     */
    Account reauthenticate(Study study, SignIn signIn);
    
    /**
     * This clears the user's reauthentication token.
     */
    void deleteReauthToken(AccountId accountId);
    
    /**
     * Create an account. If the optional consumer is passed to this method and it throws an 
     * exception, the account will not be persisted (the consumer is executed after the persist 
     * is executed in a transaction, however).
     */
    void createAccount(Study study, Account account, Consumer<Account> afterPersistConsumer);
    
    /**
     * Save account changes. Account should have been retrieved from the getAccount() method 
     * (constructAccount() is not sufficient). If the optional consumer is passed to this method and 
     * it throws an exception, the account will not be persisted (the consumer is executed after 
     * the persist is executed in a transaction, however).
     */
    void updateAccount(Account account, Consumer<Account> afterPersistConsumer);
    
    /**
     * Load, and if it exists, edit and save an account. 
     */
    void editAccount(StudyIdentifier studyId, String healthCode, Consumer<Account> accountEdits);
    
    /**
     * Get an account in the context of a study by the user's ID, email address, health code,
     * or phone number. Returns null if there is no account, it is up to callers to translate 
     * this into the appropriate exception, if any. 
     */
    Account getAccount(AccountId accountId);
    
    /**
     * Delete an account along with the authentication credentials.
     */
    void deleteAccount(AccountId accountId);
    
    /**
     * Get a page of lightweight account summaries (most importantly, the email addresses of 
     * participants which are required for the rest of the participant APIs). 
     * @param study
     *      retrieve participants in this study
     * @param search
     *      all the parameters necessary to perform a filtered search of user account summaries, including
     *      paging parameters.
     */
    PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search);
    
    /**
     * For MailChimp, and other external systems, we need a way to get a healthCode for a given email.
     */
    default String getHealthCodeForAccount(AccountId accountId) {
        Account account = getAccount(accountId);
        if (account != null) {
            return account.getHealthCode();
        } else {
            return null;
        }
    }
}
