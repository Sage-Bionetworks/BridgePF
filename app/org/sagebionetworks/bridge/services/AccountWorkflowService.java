package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccountWorkflowService {
    
    private static final String PASSWORD_RESET_TOKEN_EXPIRED = "Password reset token has expired (or already been used).";
    private static final String VERIFY_EMAIL_TOKEN_EXPIRED = "Email verification token has expired (or already been used).";
    private static final String RESET_PASSWORD_URL = "%s/mobile/resetPassword.html?study=%s&sptoken=%s";
    private static final String VERIFY_EMAIL_URL = "%s/mobile/verifyEmail.html?study=%s&sptoken=%s";
    private static final String BASE_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    private static final String EXP_WINDOW_TOKEN = "expirationWindow";
    private static final String URL_TOKEN = "url";
    static final int EXPIRE_IN_SECONDS = 60*60*2;
    
    private static class VerificationData {
        private final String studyId;
        private final String userId;
        @JsonCreator
        public VerificationData(@JsonProperty("studyId") String studyId, @JsonProperty("userId") String userId) {
            checkArgument(isNotBlank(studyId));
            checkArgument(isNotBlank(userId));
            this.studyId = studyId;
            this.userId = userId;
        }
        public String getStudyId() {
            return studyId;
        }
        public String getUserId() {
            return userId;
        }
    }
    
    private StudyService studyService;
    
    private SendMailService sendMailService;
    
    private AccountDao accountDao;
    
    private CacheProvider cacheProvider;
    
    private NotificationsService notificationsService;

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    public final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    /**
     * Send email verification token as part of creating an account that requires an email address be
     * verified. We assume that an account has been created and that email verification should be sent
     * (neither is verified in this method).
     */
    public void sendEmailVerificationToken(Study study, String userId, String recipientEmail) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        
        if (recipientEmail != null) {
            String sptoken = createTimeLimitedToken();
            
            saveVerification(sptoken, new VerificationData(study.getIdentifier(), userId));
            
            String studyId = BridgeUtils.encodeURIComponent(study.getIdentifier());
            String url = String.format(VERIFY_EMAIL_URL, BASE_URL, studyId, sptoken);
            
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withEmailTemplate(study.getVerifyEmailTemplate())
                .withRecipientEmail(recipientEmail)
                .withToken(URL_TOKEN, url).build();
            sendMailService.sendEmail(provider);         
        }
    }
    
    /**
     * Send another email verification token. This creates and sends a new verification token 
     * starting with the user's email address.
     */
    public void resendEmailVerificationToken(AccountId accountId) {
        checkNotNull(accountId);
        
        Study study = studyService.getStudy(accountId.getStudyId());
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            sendEmailVerificationToken(study, account.getId(), account.getEmail());
        }
    }
    
    /**
     * Using the verification token that was sent to the user, verify the email address. 
     * If the token is invalid, it fails quietly. If the token exists but the account 
     * does not, it throws an exception (this would be unexpected). If an account is 
     * returned, the email has been verified, but the AccountDao must be called in order 
     * to persist the state change.
     * @returns account if the account is successfully verified (otherwise, throws an exception)
     */
    public Account verifyEmail(EmailVerification verification) {
        checkNotNull(verification);

        VerificationData data = restoreVerification(verification.getSptoken());
        if (data == null) {
            throw new BadRequestException(VERIFY_EMAIL_TOKEN_EXPIRED);
        }
        Study study = studyService.getStudy(data.getStudyId());

        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), data.getUserId()));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }
    
    /**
     * Send an email message to the user notifying them that the account already exists, and 
     * provide a link to reset the password if desired. The workflow of this email then merges 
     * with the workflow to reset a password.
     */
    public void notifyAccountExists(Study study, AccountId accountId) {
        checkNotNull(study);
        checkNotNull(accountId);
        
        Account account = accountDao.getAccount(accountId);
        if (account.getEmail() != null && account.getEmailVerified()) {
            sendPasswordResetRelatedEmail(study, account.getEmail(), study.getAccountExistsTemplate());    
        } else if (account.getPhone() != null && account.getPhoneVerified()) {
            String appName = (study.getShortName() != null) ? study.getShortName() : "Bridge";
            String message = "Account for " + appName + " already exists. Reset password: ";
            sendPasswordResetRelatedSMS(study, account.getPhone(), message);
        }
    }
    
    /**
     * Request that a token be sent to the user's email address that can be used to 
     * submit a password change to the server. This method will fail silently if 
     * the email does not map to an account, in order to prevent account enumeration 
     * attacks.
     */
    public void requestResetPassword(Study study, AccountId accountId) {
        checkNotNull(accountId);
        checkArgument(study.getIdentifier().equals(accountId.getStudyId()));
        
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            if (account.getEmail() != null && account.getEmailVerified()) {
                sendPasswordResetRelatedEmail(study, account.getEmail(), study.getResetPasswordTemplate());    
            } else if (account.getPhone() != null && account.getPhoneVerified()) {
                String appName = (study.getShortName() != null) ? study.getShortName() : "Bridge";
                String message = "Reset " + appName + " password: ";
                sendPasswordResetRelatedSMS(study, account.getPhone(), message);
            }
        }
    }
    
    private void sendPasswordResetRelatedEmail(Study study, String email, EmailTemplate template) {
        String sptoken = createTimeLimitedToken();
        
        String cacheKey = sptoken + ":" + study.getIdentifier();
        cacheProvider.setString(cacheKey, email, EXPIRE_IN_SECONDS);
        
        String studyId = BridgeUtils.encodeURIComponent(study.getIdentifier());
        String url = String.format(RESET_PASSWORD_URL, BASE_URL, studyId, sptoken);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withEmailTemplate(template)
            .withRecipientEmail(email)
            .withToken(URL_TOKEN, url)
            .withToken(EXP_WINDOW_TOKEN, Integer.toString(EXPIRE_IN_SECONDS/60/60)).build();
        sendMailService.sendEmail(provider);
    }
    
    private void sendPasswordResetRelatedSMS(Study study, Phone phone, String message) {
        String sptoken = createTimeLimitedToken();        
        String cacheKey = sptoken + ":phone:" + study.getIdentifier();
        cacheProvider.setString(cacheKey, getPhoneString(phone), EXPIRE_IN_SECONDS);
        
        String studyId = BridgeUtils.encodeURIComponent(study.getIdentifier());
        String url = String.format(RESET_PASSWORD_URL, BASE_URL, studyId, sptoken);
        
        notificationsService.sendSMSMessage(study.getStudyIdentifier(), phone, message + url);
    }

    /**
     * Use a supplied password reset token to change the password on an account. If the supplied 
     * token is not valid, this method throws an exception. If the token is valid but the account 
     * does not exist, an exception is also thrown (this would be unusual).
     */
    public void resetPassword(PasswordReset passwordReset) {
        checkNotNull(passwordReset);
        
        // This pathway is unusual as the account may have an email address or a phone number, so test for both.
        String emailCacheKey = passwordReset.getSptoken() + ":" + passwordReset.getStudyIdentifier();
        String phoneCacheKey = passwordReset.getSptoken() + ":phone:" + passwordReset.getStudyIdentifier();
        
        String email = cacheProvider.getString(emailCacheKey);
        String phoneJson = cacheProvider.getString(phoneCacheKey);
        if (email == null && phoneJson == null) {
            throw new BadRequestException(PASSWORD_RESET_TOKEN_EXPIRED);
        }
        cacheProvider.removeString(emailCacheKey);
        cacheProvider.removeString(phoneCacheKey);
        
        Study study = studyService.getStudy(passwordReset.getStudyIdentifier());
        AccountId accountId = null;
        if (email != null) {
            accountId = AccountId.forEmail(study.getIdentifier(), email);
        } else if (phoneJson != null) {
            accountId = AccountId.forPhone(study.getIdentifier(), getPhone(phoneJson));
        } else {
            throw new BridgeServiceException("Could not reset password");
        }
        Account account = accountDao.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        accountDao.changePassword(account, passwordReset.getPassword());
    }

    private void saveVerification(String sptoken, VerificationData data) {
        checkArgument(isNotBlank(sptoken));
        checkNotNull(data);
                 
        try {
            cacheProvider.setString(sptoken, BridgeObjectMapper.get().writeValueAsString(data), EXPIRE_IN_SECONDS);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
             
    private VerificationData restoreVerification(String sptoken) {
        checkArgument(isNotBlank(sptoken));
                 
        String json = cacheProvider.getString(sptoken);
        if (json != null) {
            try {
                cacheProvider.removeString(sptoken);
                return BridgeObjectMapper.get().readValue(json, VerificationData.class);
            } catch (IOException e) {
                throw new BridgeServiceException(e);
            }
        }
        return null;
    }    
    
    private String getPhoneString(Phone phone) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(phone);
        } catch (JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    private Phone getPhone(String json) {
        try {
            return BridgeObjectMapper.get().readValue(json, Phone.class);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    protected String createTimeLimitedToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
}
