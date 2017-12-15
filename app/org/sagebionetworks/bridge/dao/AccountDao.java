package org.sagebionetworks.bridge.dao;

import java.util.Iterator;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;

/**
 * DAO to retrieve personally identifiable account information, including authentication 
 * credentials. To work with users, use the ParticipantService, which orchestrates calls 
 * to the AccountDao in order to reduce the number of times we make calls to our external 
 * authentication service.
 */
public interface AccountDao {
    
    /**
     * Verify an email address using a supplied, one-time token for verification.
     */
    void verifyEmail(EmailVerification verification);
    
    /**
     * Set the verified flag for the channel (email or phone) to true, and enable the account (if needed).
     */
    void verifyChannel(AuthenticationService.ChannelType channelType, Account account);
    
    /**
     * Sign up sends an email address with a link that includes a one-time token for verification. That email
     * can be resent by calling this method.
     */
    void resendEmailVerificationToken(AccountId accountId);
    
    /**
     * Request that an email be sent to the account holder with a link to reset a password, including a 
     * one-time verification token. 
     */
    void requestResetPassword(Study study, AccountId accountId);
    
    /**
     * Reset a password, supplying a new password and the one-time verification token that was sent via email 
     * to the account holder.
     */
    void resetPassword(PasswordReset passwordReset);
    
    /**
     * Call to change a password without a password reset workflow.
     */
    void changePassword(Account account, String newPassword);
    
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
     * Sign the user out of Bridge. This clears the user's reauthentication token.
     */
    void signOut(AccountId accountId);
    
    /**
     * Retrieve an account where authentication is handled outside of the DAO (If we
     * retrieve and return a session to the user through a path that does not call
     * authenticate/reauthenticate, then you will need to call this method to get
     * the final account). This retrieves the account, and rotates and returns a new
     * reauthorization token, the same as the authenticate and reauthenticate calls.
     * This method returns null if the Account does not exist.
     */
    Account getAccountAfterAuthentication(AccountId accountId);
    
    /**
     * A factory method to construct a valid Account object that will work with our
     * underlying persistence store. This does NOT save the account, you must call
     * createAccount() after the account has been updated.
     */
    Account constructAccount(Study study, String email, Phone phone, String password);
    
    /**
     * Create an account. The account object should initially be retrieved from the 
     * constructAccount() factory method. Returns the created account's ID.
     */
    String createAccount(Study study, Account account, boolean sendVerifyEmail);
    
    /**
     * Save account changes. Account should have been retrieved from the getAccount() method 
     * (constructAccount() is not sufficient).
     */
    void updateAccount(Account account);
    
    /**
     * Get an account in the context of a study by the user's ID, email address, or phone 
     * number. Returns null if there is no account, it is up to callers to translate this 
     * into the appropriate exception, if any. 
     */
    Account getAccount(AccountId accountId);
    
    /**
     * Delete an account along with the authentication credentials.
     */
    void deleteAccount(AccountId accountId);
    
    /**
     * Get all account summaries in all studies in a given environment.
     */
    Iterator<AccountSummary> getAllAccounts();
    
    /**
     * Get all account summaries in one study in a given environment.
     */
    Iterator<AccountSummary> getStudyAccounts(Study study);
    
    /**
     * Get a page of lightweight account summaries (most importantly, the email addresses of 
     * participants which are required for the rest of the participant APIs). 
     * @param study
     *      retrieve participants in this study
     * @param offsetBy
     *      index to start the next page of records
     * @param pageSize
     *      number of records to return (or the number of remaining records if less than the pageSize).
     * @param emailFilter
     *      a substring that will be matched (ignoring case) against the email addresses of the accounts.
     * @param phoneFilter
     *      a substring that will be matched (ignoring case) against the phone number of the account. String will be 
     *      converted to E164 format for comparison against the stored phone number.
     * @param startDate
     *      a date and time on or after which the account should have been created in order to match the query.
     * @param endDate
     *      a date and time on or before which the account should have been created in order to match the query.
     * @return
     *      a paged resource list that includes the page of account summaries, as well as other information 
     *      about the request and the total number of records.
     */
    PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize,
            String emailFilter, String phoneFilter, DateTime startDate, DateTime endDate);
    
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
