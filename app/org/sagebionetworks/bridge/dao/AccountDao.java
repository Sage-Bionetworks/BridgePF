package org.sagebionetworks.bridge.dao;

import java.util.Iterator;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * DAO to retrieve personally identifiable account information, including authentication 
 * credentials.
 *
 */
public interface AccountDao {

    /**
     * Create an account within the context of the study. Sending email address confirmation
     * and sign up confirmation emails is optional. 
     * @param study
     * @param signUp
     * @param sendEmail
     * @return account
     */
    public Account signUp(Study study, SignUp signUp, boolean sendEmail);
    
    /**
     * Verify an email address using a supplied, one-time token for verification.
     * @param study
     * @param verification
     * @return
     */
    public Account verifyEmail(StudyIdentifier study, EmailVerification verification);
    
    /**
     * Sign up sends an email address with a link that includes a one-time token for verification. That email
     * can be resent by calling this method.
     * @param studyIdentifier
     * @param email
     */
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email);
    
    /**
     * Request that an email be sent to the account holder with a link to reset a password, including a 
     * one-time verification token. 
     * @param email
     */
    public void requestResetPassword(Study study, Email email);
    
    /**
     * Reset a password, supplying a new password and the one-time verification token that was sent via email 
     * to the account holder.
     * @param passwordReset
     */
    public void resetPassword(PasswordReset passwordReset);
    
    /**
     * Authenticate a user with the supplied credentials, returning that user's account record
     * if successful. 
     * @param study
     * @param signIn
     * @return
     */
    public Account authenticate(Study study, SignIn signIn);
    
    /**
     * Get an account in the context of a study by the email address. Returns null if 
     * there is no account, it is up to callers to translate this into the appropriate 
     * exception, if any. 
     * @param study
     * @param email
     * @return
     */
    public Account getAccount(Study study, String email);
    
    /**
     * Save account changes.
     * @param study
     * @param account
     */
    public void updateAccount(Study study, Account account);
    
    /**
     * Delete an account along with the authentication credentials.
     * @param study
     * @param email
     */
    public void deleteAccount(Study study, String email);
    
    /**
     * Get all accounts in all studies in a given environment.
     * @return
     */
    public Iterator<Account> getAllAccounts();
    
    /**
     * Get all accounts in one study in a given environment.
     * @param study
     * @return
     */
    public Iterator<Account> getStudyAccounts(Study study);
    
    /**
     * Get a page of lightweight account summaries (most importantly, the email addresses of 
     * participants which are required for the rest of the participant APIs). 
     * @param study
     *      retrieve participants in this study
     * @param offsetBy
     *      index to start the next page of records
     * @param pageSize
     *      number of records to return (or the number of remaining records if less than the pageSize).
     * @return
     *      a paged resource list that includes the page of account summaries, as well as other information 
     *      about the request and the total number of records.
     */
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize);
}
