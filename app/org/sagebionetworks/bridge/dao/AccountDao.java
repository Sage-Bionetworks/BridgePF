package org.sagebionetworks.bridge.dao;

import java.util.Iterator;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * DAO to retrieve personally identifiable account information, including authentication 
 * credentials.
 *
 */
public interface AccountDao {

    /**
     * Create an account within the context of the study. Sending an email to verify the user's 
     * email address is optional. Creating an account *always* creates a healthCode and assigns 
     * the healthId for that healthCode to the created account.
     */
    public Account signUp(Study study, StudyParticipant participant, boolean sendEmail);
    
    /**
     * Verify an email address using a supplied, one-time token for verification.
     * @return
     */
    public Account verifyEmail(StudyIdentifier study, EmailVerification verification);
    
    /**
     * Sign up sends an email address with a link that includes a one-time token for verification. That email
     * can be resent by calling this method.
     */
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email);
    
    /**
     * Request that an email be sent to the account holder with a link to reset a password, including a 
     * one-time verification token. 
     */
    public void requestResetPassword(Study study, Email email);
    
    /**
     * Reset a password, supplying a new password and the one-time verification token that was sent via email 
     * to the account holder.
     */
    public void resetPassword(PasswordReset passwordReset);
    
    /**
     * Authenticate a user with the supplied credentials, returning that user's account record
     * if successful. 
     */
    public Account authenticate(Study study, SignIn signIn);
    
    /**
     * Get an account in the context of a study by the user's ID or by their email address (email is 
     * deprecated and in the process of being removed). Returns null if there is no account, it is 
     * up to callers to translate this into the appropriate exception, if any. 
     */
    public Account getAccount(Study study, String id);
    
    /**
     * Save account changes.
     */
    public void updateAccount(Account account);
    
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
     * @param emailFilter
     *      a substring that will be matched (ignoring case) against the email addresses of the accounts.
     * @return
     *      a paged resource list that includes the page of account summaries, as well as other information 
     *      about the request and the total number of records.
     */
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize, String emailFilter);
    
    /**
     * For MailChimp, and other external systems, we need a way to get a healthCode for a given email.
     */
    public String getHealthCodeForEmail(Study study, String email);
}
