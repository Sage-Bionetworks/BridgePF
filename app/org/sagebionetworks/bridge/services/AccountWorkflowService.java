package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;
import static org.sagebionetworks.bridge.validators.SignInValidator.EMAIL_SIGNIN_REQUEST;
import static org.sagebionetworks.bridge.validators.SignInValidator.PHONE_SIGNIN_REQUEST;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class AccountWorkflowService {
    private static final String BASE_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    static final String CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS = "channel.throttle.max.requests";
    static final String CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS = "channel.throttle.timeout.seconds";
    private static final String PASSWORD_RESET_TOKEN_EXPIRED = "Password reset token has expired (or already been used).";
    private static final String VERIFY_EMAIL_TOKEN_EXPIRED = "Email verification token has expired (or already been used).";
    
    private static final String RESET_PASSWORD_URL = "/mobile/resetPassword.html?study=%s&sptoken=%s";
    private static final String VERIFY_EMAIL_URL = "/mobile/verifyEmail.html?study=%s&sptoken=%s";
    private static final String EMAIL_SIGNIN_URL = "/mobile/%s/startSession.html?email=%s&study=%s&token=%s";
    
    private static final String SHORT_RESET_PASSWORD_URL = "/rp?study=%s&sptoken=%s";
    private static final String SHORT_VERIFY_EMAIL_URL = "/ve?study=%s&sptoken=%s";
    private static final String SHORT_EMAIL_SIGNIN_URL = "/s/%s?email=%s&token=%s";
    
    private static final String EXP_WINDOW_TOKEN = "expirationWindow";
    
    private static final String EMAIL_KEY = "email";
    private static final String TOKEN_KEY = "token";
    private static final String SPTOKEN_KEY = "sptoken";
    
    private static final String URL_KEY = "url";
    private static final String SHORT_URL_KEY = "shortUrl";
     
    private static final String EMAIL_SIGNIN_URL_KEY = "emailSignInUrl";
    private static final String SHORT_EMAIL_SIGNIN_URL_KEY = "shortEmailSignInUrl";
    
    private static final String RESET_PASSWORD_URL_KEY = "resetPasswordUrl";
    private static final String SHORT_RESET_PASSWORD_URL_KEY = "shortResetPasswordUrl";
        
    private static final String EMAIL_SIGNIN_REQUEST_KEY = "%s:%s:signInRequest";
    private static final String PHONE_SIGNIN_REQUEST_KEY = "%s:%s:phoneSignInRequest";
    
    private final AtomicLong emailSignInRequestInMillis = new AtomicLong(200L);
    private final AtomicLong phoneSignInRequestInMillis = new AtomicLong(200L);
    
    static final int VERIFY_OR_RESET_EXPIRE_IN_SECONDS = 60*60*2; // 2 hours 
    static final int SIGNIN_EXPIRE_IN_SECONDS = 60*60; // 1 hour

    // Enumeration of request types that we want to throttle on. Used to differentiate between request types and
    // generate cache keys.
    private enum ThrottleRequestType {
        EMAIL_SIGNIN,
        PHONE_SIGNIN,
        VERIFY_EMAIL,
    }

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

    // Config values
    private int channelThrottleMaxRequests;
    private int channelThrottleTimeoutSeconds;

    // Dependent services
    private JedisOps jedisOps;
    private StudyService studyService;
    private SendMailService sendMailService;
    private AccountDao accountDao;
    private CacheProvider cacheProvider;
    private NotificationsService notificationsService;

    /** Bridge config, used to get config values such as throttle configuration. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.channelThrottleMaxRequests = bridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS);
        this.channelThrottleTimeoutSeconds = bridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS);
    }

    /** JedisOps, used for basic Redis operations, like expire() and incr(). */
    @Autowired
    public final void setJedisOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    final AtomicLong getEmailSignInRequestInMillis() {
        return emailSignInRequestInMillis;
    }
    
    final AtomicLong getPhoneSignInRequestInMillis() {
        return phoneSignInRequestInMillis;
    }

    /**
     * Send email verification token as part of creating an account that requires an email address be
     * verified. We assume that an account has been created and that email verification should be sent
     * (neither is verified in this method).
     */
    public void sendEmailVerificationToken(Study study, String userId, String recipientEmail) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        
        if (recipientEmail == null) {
            // Can't send email verification if there's no email.
            return;
        }

        if (isRequestThrottled(ThrottleRequestType.VERIFY_EMAIL, userId)) {
            // Too many requests. Throttle.
            return;
        }

        String sptoken = getNextToken();

        saveVerification(sptoken, new VerificationData(study.getIdentifier(), userId));

        String url = getVerifyEmailURL(study, sptoken);
        String shortUrl = getShortVerifyEmailURL(study, sptoken);

        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withEmailTemplate(study.getVerifyEmailTemplate())
                .withRecipientEmail(recipientEmail)
                .withToken(SPTOKEN_KEY, sptoken)
                .withToken(URL_KEY, url)
                .withToken(SHORT_URL_KEY, shortUrl)
                .withExpirationPeriod(VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
                .build();
        sendMailService.sendEmail(provider);
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
            sendPasswordResetRelatedEmail(study, account.getEmail(), true, study.getAccountExistsTemplate());    
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
                sendPasswordResetRelatedEmail(study, account.getEmail(), false, study.getResetPasswordTemplate());    
            } else if (account.getPhone() != null && account.getPhoneVerified()) {
                String appName = (study.getShortName() != null) ? study.getShortName() : "Bridge";
                String message = "Reset " + appName + " password: ";
                sendPasswordResetRelatedSMS(study, account.getPhone(), message);
            }
        }
    }
    
    private void sendPasswordResetRelatedEmail(Study study, String email, boolean includeEmailSignIn,
            EmailTemplate template) {
        String sptoken = getNextToken();
        
        String cacheKey = sptoken + ":" + study.getIdentifier();
        cacheProvider.setObject(cacheKey, email, VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        
        String url = getResetPasswordURL(study, sptoken);
        String shortUrl = getShortResetPasswordURL(study, sptoken);
        
        BasicEmailProvider.Builder builder = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withEmailTemplate(template)
            .withRecipientEmail(email)
            // for backwards compatibility; we also set as passwordResetUrl
            .withToken(RESET_PASSWORD_URL_KEY, url)
            .withToken(SHORT_RESET_PASSWORD_URL_KEY, shortUrl)
            .withToken(SPTOKEN_KEY, sptoken)
            .withToken(URL_KEY, url)
            .withToken(SHORT_URL_KEY, shortUrl)
            .withExpirationPeriod(VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
            .withToken(EXP_WINDOW_TOKEN, Integer.toString(VERIFY_OR_RESET_EXPIRE_IN_SECONDS/60/60));
            
        if (includeEmailSignIn && study.isEmailSignInEnabled()) {
            SignIn signIn = new SignIn.Builder().withEmail(email).withStudy(study.getIdentifier()).build();
            requestChannelSignIn(EMAIL, EMAIL_SIGNIN_REQUEST, EMAIL_CACHE_KEY_FUNC, emailSignInRequestInMillis,
                signIn, false, this::getNextToken, (theStudy, token) -> {
                    // get and add the sign in URLs.
                    String emailUrl = getEmailSignInURL(signIn.getEmail(), theStudy.getIdentifier(), token);
                    String emailShortUrl = getShortEmailSignInURL(signIn.getEmail(), theStudy.getIdentifier(), token);
                    
                    // Put the components in separately, in case we want to alter the URL in a specific template.
                    builder.withToken(EMAIL_KEY, BridgeUtils.encodeURIComponent(signIn.getEmail()));
                    builder.withToken(TOKEN_KEY, token); // NOTE: sptoken and token are different
                    builder.withToken(EMAIL_SIGNIN_URL_KEY, emailUrl);
                    builder.withToken(SHORT_EMAIL_SIGNIN_URL_KEY, emailShortUrl);
                });
        }
        sendMailService.sendEmail(builder.build());
    }
    
    private void sendPasswordResetRelatedSMS(Study study, Phone phone, String message) {
        String sptoken = getNextToken();
        String cacheKey = sptoken + ":phone:" + study.getIdentifier();
        cacheProvider.setObject(cacheKey, getPhoneString(phone), VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        
        String url = getShortResetPasswordURL(study, sptoken);

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
        
        String email = cacheProvider.getObject(emailCacheKey, String.class);
        String phoneJson = cacheProvider.getObject(phoneCacheKey, String.class);
        if (email == null && phoneJson == null) {
            throw new BadRequestException(PASSWORD_RESET_TOKEN_EXPIRED);
        }
        cacheProvider.removeObject(emailCacheKey);
        cacheProvider.removeObject(phoneCacheKey);
        
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
    
    /**
     * Request a token to be sent via SMS to the user, that can be used to start a session on the Bridge server.
     */
    public void requestPhoneSignIn(SignIn signIn) {
        requestChannelSignIn(PHONE, PHONE_SIGNIN_REQUEST, PHONE_CACHE_KEY_FUNC, phoneSignInRequestInMillis, 
                signIn, true, this::getNextPhoneToken, (study, token) -> {
            // Put a dash in the token so it's easier to enter into the UI. All this should
            // eventually come from a template
            String formattedToken = token.substring(0,3) + "-" + token.substring(3,6); 
            String appName = (study.getShortName() != null) ? study.getShortName() : "Bridge";
            String message = "Enter " + formattedToken + " to sign in to " + appName;
            
            notificationsService.sendSMSMessage(study.getStudyIdentifier(), signIn.getPhone(), message);
        });
    }
    
    /**
     * Request a token to be sent via a link in an email message, that can be used to start a session on the Bridge server. 
     * The installed application should intercept this link in order to complete the transaction within the app, where the 
     * returned session can be captured. If the link is not captured, it retrieves a test page on the Bridge server as 
     * configured by default. That test page will complete the transaction and return a session token.
     */
    public void requestEmailSignIn(SignIn signIn) {
        requestChannelSignIn(EMAIL, EMAIL_SIGNIN_REQUEST, EMAIL_CACHE_KEY_FUNC, emailSignInRequestInMillis, 
                signIn, true, this::getNextToken, (study, token) -> {
            String url = getEmailSignInURL(signIn.getEmail(), study.getIdentifier(), token);
            String shortUrl = getShortEmailSignInURL(signIn.getEmail(), study.getIdentifier(), token);
            
            // Email is URL encoded, which is probably a mistake. We're now providing an URL that's will be 
            // opaque to the user, like the other APIs (where the templates just have a ${url} variable), but we 
            // need to provide host/email/studyId/token variables for earlier versions of the email sign in template 
            // that had the URL spelled out with substitutions. The email was encoded so it could be substituted 
            // into that template.
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withEmailTemplate(study.getEmailSignInTemplate())
                .withStudy(study)
                .withRecipientEmail(signIn.getEmail())
                .withToken(EMAIL_KEY, BridgeUtils.encodeURIComponent(signIn.getEmail()))
                .withToken(TOKEN_KEY, token)
                .withToken(URL_KEY, url)
                .withToken(SHORT_URL_KEY, shortUrl)
                .withExpirationPeriod(SIGNIN_EXPIRE_IN_SECONDS)
                .build();
            sendMailService.sendEmail(provider);
        });
    }
    
    private void requestChannelSignIn(ChannelType channelType, Validator validator,
            Function<SignIn, String> cacheKeySupplier, AtomicLong atomicLong, SignIn signIn, boolean shouldThrottle,
            Supplier<String> tokenSupplier, BiConsumer<Study, String> messageSender) {
        long startTime = System.currentTimeMillis();
        Validate.entityThrowingException(validator, signIn);

        // We use the study so it's existence is verified. We retrieve the account so we verify it
        // exists as well. If the token is returned to the server, we can safely use the credentials 
        // in the persisted SignIn object.
        Study study = studyService.getStudy(signIn.getStudyId());

        // Do we want the same flag for phone? Do we want to eliminate this flag?
        if (channelType == EMAIL && !study.isEmailSignInEnabled()) {
            throw new UnauthorizedException("Email-based sign in not enabled for study: " + study.getName());
        }

        // check that the account exists, return quietly if not to prevent account enumeration attacks
        Account account = accountDao.getAccount(signIn.getAccountId());
        if (account == null) {
            try {
                // The not found case returns *much* faster than the normal case. To prevent account enumeration 
                // attacks, measure time of a successful case and delay for that period before returning.
                TimeUnit.MILLISECONDS.sleep(atomicLong.get());            
            } catch(InterruptedException e) {
                // Just return, the thread was killed by the connection, the server died, etc.
            }
            return;
        }

        ThrottleRequestType throttleType = channelType == EMAIL ? ThrottleRequestType.EMAIL_SIGNIN :
                ThrottleRequestType.PHONE_SIGNIN;
        if (shouldThrottle && isRequestThrottled(throttleType, account.getId())) {
            // Too many requests. Throttle.
            return;
        }

        String cacheKey = cacheKeySupplier.apply(signIn);
        String token = cacheProvider.getObject(cacheKey, String.class);
        if (token == null) {
            token = tokenSupplier.get();
            cacheProvider.setObject(cacheKey, token, SIGNIN_EXPIRE_IN_SECONDS);
        }

        messageSender.accept(study, token);
        atomicLong.set(System.currentTimeMillis()-startTime);
    }

    /**
     * Attempts to validate a sign in request using a token that was stored and then sent 
     * via SMS or an email message. 
     * 
     * @return AccountId the accountId of the account if the sign in is successful.
     * @throws AuthenticationFailedException
     *             if the token is missing or invalid (not a successful sign in attempt).
     */
    public AccountId channelSignIn(ChannelType channelType, CriteriaContext context, SignIn signIn,
            Validator validator) {
        Validate.entityThrowingException(validator, signIn);
       
        String cacheKey = (channelType == EMAIL) ?
                EMAIL_CACHE_KEY_FUNC.apply(signIn) :
                PHONE_CACHE_KEY_FUNC.apply(signIn);

        String storedToken = cacheProvider.getObject(cacheKey, String.class);
        if (storedToken == null || !storedToken.equals(signIn.getToken())) {
            throw new AuthenticationFailedException();
        }
        // Consume the key regardless of what happens
        cacheProvider.removeObject(cacheKey);
        
        return signIn.getAccountId();
    }
    
    private void saveVerification(String sptoken, VerificationData data) {
        checkArgument(isNotBlank(sptoken));
        checkNotNull(data);
                 
        try {
            cacheProvider.setObject(sptoken, BridgeObjectMapper.get().writeValueAsString(data), VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
             
    private VerificationData restoreVerification(String sptoken) {
        checkArgument(isNotBlank(sptoken));
                 
        String json = cacheProvider.getObject(sptoken, String.class);
        if (json != null) {
            try {
                cacheProvider.removeObject(sptoken);
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
    
    // Provided via accessor so it can be mocked for tests
    protected String getNextToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
    
    // Provided via accessor so it can be mocked for tests
    protected String getNextPhoneToken() {
        return SecureTokenGenerator.PHONE_CODE_INSTANCE.nextToken();
    }
    
    private static Function<SignIn, String> PHONE_CACHE_KEY_FUNC = (signIn) -> {
        return String.format(PHONE_SIGNIN_REQUEST_KEY, signIn.getPhone().getNumber(), signIn.getStudyId());
    };
    
    private static Function<SignIn, String> EMAIL_CACHE_KEY_FUNC = (signIn) -> {
        return String.format(EMAIL_SIGNIN_REQUEST_KEY, signIn.getEmail(), signIn.getStudyId());
    };

    private String getEmailSignInURL(String email, String studyId, String token) {
        return formatWithEncodedArgs(EMAIL_SIGNIN_URL, studyId, email, studyId, token);
    }
    
    private String getVerifyEmailURL(Study study, String sptoken) {
        return formatWithEncodedArgs(VERIFY_EMAIL_URL, study.getIdentifier(), sptoken);
    }
    
    private String getResetPasswordURL(Study study, String sptoken) {
        return formatWithEncodedArgs(RESET_PASSWORD_URL, study.getIdentifier(), sptoken);
    }
    
    private String getShortEmailSignInURL(String email, String studyId, String token) {
        return formatWithEncodedArgs(SHORT_EMAIL_SIGNIN_URL, studyId, email, token);
    }
    
    private String getShortVerifyEmailURL(Study study, String sptoken) {
        return formatWithEncodedArgs(SHORT_VERIFY_EMAIL_URL, study.getIdentifier(), sptoken);
    }
    
    private String getShortResetPasswordURL(Study study, String sptoken) {
        return formatWithEncodedArgs(SHORT_RESET_PASSWORD_URL, study.getIdentifier(), sptoken);
    }
    
    private String formatWithEncodedArgs(String formatString, String... strings) {
        for (int i=0; i < strings.length; i++) {
            strings[i] = BridgeUtils.encodeURIComponent(strings[i]);
        }
        return BASE_URL + String.format(formatString, (Object[])strings);
    }

    // Check if the request is throttled. Key is either email address or phone, depending on the type.
    private boolean isRequestThrottled(ThrottleRequestType type, String userId) {
        // Generate key, which is in the form of channel-throttling:[type]:[userId].
        String cacheKey = "channel-throttling:" + type.toString().toLowerCase() + ":" + userId;

        // We use Jedis to atomically increment the key value, and then set an expiration on it. The return value of
        // exec() is the list of Jedis results in order. For the incr(), the result is the value after incrementing.
        // This is the number of times we've requested in the last time period. If this exceeds the max requests, we're
        // throttled.
        int numRequests;
        try (JedisTransaction transaction = jedisOps.getTransaction(cacheKey)) {
            List<Object> resultList = transaction.incr(cacheKey).expire(cacheKey, channelThrottleTimeoutSeconds)
                    .exec();
            numRequests = ((Long) resultList.get(0)).intValue();
        }
        return numRequests > channelThrottleMaxRequests;
    }
}
