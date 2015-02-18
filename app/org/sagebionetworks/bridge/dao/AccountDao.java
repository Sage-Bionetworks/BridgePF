package org.sagebionetworks.bridge.dao;

import java.util.Iterator;

import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.accounts.Account;
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
     */
    public void signUp(Study study, SignUp signUp, boolean sendEmail);
    
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
     * @param study
     * @param email
     */
    public void resendEmailVerificationToken(Study study, Email email);
    
    /**
     * Request that an email be sent to the account holder with a link to reset a password, including a 
     * one-time verification token. 
     * @param email
     */
    public void requestResetPassword(Email email);
    
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
     * Get an account in the context of a study by the email address. 
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
}
