package org.sagebionetworks.bridge.okta;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.SendMailService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.okta.sdk.clients.AuthApiClient;
import com.okta.sdk.clients.UserApiClient;
import com.okta.sdk.exceptions.ApiException;
import com.okta.sdk.framework.ErrorCause;
import com.okta.sdk.framework.FilterBuilder;
import com.okta.sdk.framework.PagedResults;
import com.okta.sdk.models.auth.AuthResult;
import com.okta.sdk.models.users.LoginCredentials;
import com.okta.sdk.models.users.Password;
import com.okta.sdk.models.users.User;
import com.okta.sdk.models.users.UserProfile;

@Component("oktaAccountDao")
public class OktaAccountDao implements AccountDao {

    private static final Logger LOG = LoggerFactory.getLogger(OktaAccountDao.class);
    private static final String BASE_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    
    private StudyService studyService;
    private SubpopulationService subpopService;
    private HealthCodeService healthCodeService;
    private CacheProvider cacheProvider;
    private SendMailService sendMailService;
    private AuthApiClient authApiClient;
    private UserApiClient userApiClient;
    private SortedMap<Integer, BridgeEncryptor> encryptors = Maps.newTreeMap();
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Autowired
    final void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    @Autowired
    final void setAuthApiClient(AuthApiClient authApiClient) {
        this.authApiClient = authApiClient;
    }
    @Autowired
    final void setUserApiClient(UserApiClient userApiClient) {
        this.userApiClient = userApiClient;
    }
    @Resource(name="encryptorList")
    final void setEncryptors(List<BridgeEncryptor> list) {
        for (BridgeEncryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
        }
    }
    
    @Override
    public void verifyEmail(EmailVerification verification) {
        checkNotNull(verification);

        String userId = restoreVerification(verification.getSptoken());
        try {
            User user = userApiClient.getUser(userId);
            // This would be rare, but it's possible if admin is changing the user
            if (user == null) {
                throw new EntityNotFoundException(Account.class);
            }
            user.getProfile().getUnmapped().put(OktaAccount.STATUS, AccountStatus.ENABLED.name());
            userApiClient.updateUser(user);
        } catch (IOException e) {
            rethrowException(e, userId);
        }
    }

    @Override
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email) {
        checkNotNull(studyIdentifier);
        checkNotNull(email);
        
        Study study = studyService.getStudy(studyIdentifier);
        try {
            User user = getUserByEmailWithoutThrowing(userApiClient, email.getEmail());
            if (user != null) {
                sendEmailVerificationToken(study, user.getId(), email.getEmail());    
            }
        } catch(IOException e) {
            rethrowException(e, null);
        }
    }

    @Override
    public void requestResetPassword(Study study, Email email) {
        checkNotNull(study);
        checkNotNull(email);
        
        try {
            User user = getUserByEmailWithoutThrowing(userApiClient, email.getEmail());
            if (user != null) {
                String sptoken = createTimeLimitedToken();
                String cacheKey = sptoken + ":" + study.getIdentifier();
                cacheProvider.setString(cacheKey, email.getEmail(), 60*5);
                
                String studyId = BridgeUtils.encodeURIComponent(study.getIdentifier());
                String url = String.format("%s/mobile/resetPassword.html?study=%s&sptoken=%s",
                        BASE_URL, studyId, sptoken);
                
                BasicEmailProvider provider = new BasicEmailProvider.Builder()
                    .withStudy(study)
                    .withEmailTemplate(study.getResetPasswordTemplate())
                    .withRecipientEmail(email.getEmail())
                    .withToken("url", url).build();
                sendMailService.sendEmail(provider);
            }
        } catch(IOException e) {
            rethrowException(e, null);
        }
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) {
        checkNotNull(passwordReset);
        
        try {
            String cacheKey = passwordReset.getSptoken() + ":" + passwordReset.getStudyIdentifier();
            String email = cacheProvider.getString(cacheKey);
            if (email != null) {
                cacheProvider.removeString(cacheKey);
                
                User user = getUserByEmailWithoutThrowing(userApiClient, email);
                if (user != null) {
                    userApiClient.setPassword(user.getId(), passwordReset.getPassword());    
                }
            }
        } catch(IOException e) {
            rethrowException(e, null);
        }
    }
    
    @Override
    public void changePassword(Account account, String newPassword) {
        checkNotNull(account);
        checkArgument(isNotBlank(newPassword));
        
        try {
            userApiClient.setPassword(account.getId(), newPassword);
        } catch(IOException e) {
            rethrowException(e, null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        checkNotNull(study);
        checkNotNull(signIn);
        
        Account account = null;
        try {
            AuthResult result = authApiClient.authenticate(signIn.getEmail(), signIn.getPassword(), null);
            
            Map<String,Object> userMap = (Map<String,Object>)result.getEmbedded().get("user");
            String userId = (String)userMap.get("id");
            User user = userApiClient.getUser(userId);
            
            account = constructAccount(study.getStudyIdentifier(), user);
            if (account.getStatus() == AccountStatus.DISABLED) {
                throw new AccountDisabledException();
            } else if (account.getStatus() == AccountStatus.UNVERIFIED) {
                throw new EntityNotFoundException(Account.class);
            }
        } catch(IOException e) {
            rethrowException(e, null);
        }
        return account;
    }

    @Override
    public Account constructAccount(Study study, String email, String password) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        checkArgument(isNotBlank(password));
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);

        Password passwordObj = new Password();
        passwordObj.setValue(password);
        
        UserProfile profile = new UserProfile();
        profile.setEmail(email);
        profile.setLogin(email);

        LoginCredentials credentials = new LoginCredentials();
        credentials.setPassword(passwordObj);
        
        User user = new User();
        user.setProfile(profile);
        user.setCredentials(credentials);
        
        Account account = new OktaAccount(study.getStudyIdentifier(), subpopGuids, user, profile, encryptors);
        
        HealthId healthId = healthCodeService.createMapping(study);
        account.setHealthId(healthId);
        
        return account;        
    }

    @Override
    public void createAccount(Study study, Account account, boolean sendVerifyEmail) {
        checkNotNull(study);
        checkNotNull(account);
        
        account.setStatus(sendVerifyEmail ? AccountStatus.UNVERIFIED : AccountStatus.ENABLED);
        try {
            User user = ((OktaAccount)account).getUser();
            User response = userApiClient.createUser(user, true);
            ((OktaAccount)account).setUser(response);
            
            if (sendVerifyEmail) {
                sendEmailVerificationToken(study, response.getId(), account.getEmail());    
            }
        } catch(IOException e) {
            rethrowException(e, account.getId());
        }        
    }

    @Override
    public void updateAccount(Account account) {
        checkNotNull(account);
        
        try {
            User user = ((OktaAccount)account).getUser();
            User result = userApiClient.updateUser(user);
            ((OktaAccount)account).setUser(result);
        } catch(IOException e) {
            rethrowException(e, account.getId());
        }
    }

    @Override
    public Account getAccount(Study study, String id) {
        checkNotNull(study);
        checkArgument(isNotBlank(id));
        
        Account account = null;
        try {
            User user = userApiClient.getUser(id);
            account = constructAccount(study.getStudyIdentifier(), user);
        } catch(IOException e) {
            rethrowException(e, id);
        }
        return account;
    }

    @Override
    public Account getAccountWithEmail(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        Account account = null;
        try {
            User user = getUserByEmailWithoutThrowing(userApiClient, email);
            if (user != null) {
                account = constructAccount(study.getStudyIdentifier(), user);    
            }
        } catch(IOException e) {
            rethrowException(e, email);
        }
        return account;
    }

    @Override
    public void deleteAccount(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        
        try {
            User user = userApiClient.getUser(userId);
            if (user != null) {
                userApiClient.deactivateUser(userId);
                userApiClient.deleteUser(userId);
            }
        } catch(IOException e) {
            rethrowException(e, userId);
        }
    }

    @Override
    public Iterator<AccountSummary> getAllAccounts() {
        Iterator<AccountSummary> combinedIterator = null;
        for (Study study : studyService.getStudies()) {
            Iterator<AccountSummary> studyIterator = getStudyAccounts(study);
            if (combinedIterator ==  null) {
                combinedIterator = studyIterator;
            } else {
                combinedIterator = Iterators.concat(combinedIterator, studyIterator);    
            }
        }
        return combinedIterator;
    }

    @Override
    public Iterator<AccountSummary> getStudyAccounts(Study study) {
        checkNotNull(study);
        
        return new OktaAccountIterator(study.getStudyIdentifier(), userApiClient);
    }

    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, String offsetBy, int pageSize,
            String emailFilter, DateTime startDate, DateTime endDate) {
        
        try {
            boolean addClause = false;
            FilterBuilder filter = new FilterBuilder();
            if (emailFilter != null) {
                filter = filter.where("profile.email").startsWith(emailFilter);
                addClause = true;
            }
            if (startDate != null) {
                if (addClause) {
                    filter = filter.and();
                }
                filter = filter.where("user.created").greaterThanOrEqual(startDate);
                addClause = true;
            }
            if (endDate != null) {
                if (addClause) {
                    filter = filter.and();
                }
                filter = filter.where("user.created").lessThanOrEqual(endDate);
            }
            PagedResults<User> results = null;
            if (isNotBlank(offsetBy)) {
                results = userApiClient.getUsersPagedResultsWithAdvancedSearchAndLimitAndAfterCursor(
                        filter, pageSize, offsetBy);
            } else {
                results = userApiClient.getUsersPagedResultsWithAdvancedSearchAndLimit(filter, pageSize);
            }

            List<AccountSummary> summaries = Lists.newArrayListWithCapacity(results.getResult().size());
            for (User user : results.getResult()) {
                summaries.add(AccountSummary.create(study.getStudyIdentifier(), user));
            }
            // TODO: You cannot get a total of accounts in a summary.
            PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, null, pageSize,
                    summaries.size());
            if (!results.isLastPage()) {
                page.withFilter("offsetBy", results.getNextUrl());
            }
            return page;
            
        } catch(IOException e) {
            rethrowException(e, emailFilter);
        }
        return null;
    }
    
    @Override
    public String getHealthCodeForEmail(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        try {
            User user = getUserByEmailWithoutThrowing(userApiClient, email);
            if (user != null) {
                Account account = constructAccount(study.getStudyIdentifier(), user);
                return account.getHealthCode();
            }
        } catch(IOException e) {
            rethrowException(e, email);
        }
        return null;
    }
    
    protected void sendEmailVerificationToken(Study study, String userId, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        checkArgument(isNotBlank(email));

        String sptoken = createTimeLimitedToken();
        
        saveVerification(sptoken, userId);
        
        String studyId = BridgeUtils.encodeURIComponent(study.getIdentifier());
        String url = String.format("%s/mobile/verifyEmail.html?study=%s&sptoken=%s",
                BASE_URL, studyId, sptoken);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withEmailTemplate(study.getVerifyEmailTemplate())
            .withRecipientEmail(email)
            .withToken("url", url).build();
        sendMailService.sendEmail(provider);         
    }
    
    private void saveVerification(String sptoken, String userId) {
        checkArgument(isNotBlank(sptoken));
        checkArgument(isNotBlank(userId));
        
        cacheProvider.setString(sptoken, userId, 60*5);
    }
    
    private String restoreVerification(String sptoken) {
        checkArgument(isNotBlank(sptoken));
        
        String userId = cacheProvider.getString(sptoken);
        if (userId == null) {
            throw new BridgeServiceException(
                    "Email verification token not found. You may have already enabled this account.", 404);
        }
        return userId;
    }
    
    private User getUserByEmailWithoutThrowing(UserApiClient userApiClient, String email) throws IOException {
        checkNotNull(userApiClient);
        checkNotNull(isNotBlank(email));
        
        FilterBuilder filter = new FilterBuilder().where("profile.login").equalTo(email)
                .and().where("status").equalTo("ACTIVE");
        List<User> users = userApiClient.getUsersWithAdvancedSearch(filter);
        if (users.size() > 1) {
            LOG.warn("Query for email address returned more than one user account: " + email);
        }
        return (users.isEmpty()) ? null : users.get(0);
    }
    
    private void rethrowException(IOException e, String userId) {
        if (e instanceof ApiException) {
            ApiException ae = (ApiException)e;
            int statusCode = ae.getStatusCode();
            
            List<String> errorMessages = ae.getErrorResponse().getErrorCauses().stream()
                    .map(ErrorCause::getErrorSummary).collect(Collectors.toList());
            String details = BridgeUtils.SEMICOLON_SPACE_JOINER.join(errorMessages);
            
            LOG.info(String.format("Okta error: %s: %s", statusCode, ae.getMessage()));
            throw new BridgeServiceException(ae.getErrorResponse().getErrorSummary() + "; " + details, statusCode); 
        }
        throw new BridgeServiceException(e);
    }
    
    private Account constructAccount(StudyIdentifier studyId, User user) {
        checkNotNull(studyId);
        checkNotNull(user);
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(studyId);
        OktaAccount account = new OktaAccount(studyId, subpopGuids, user, user.getProfile(), encryptors);
        
        HealthId healthId = null;
        if (account.getHealthCode() == null) {
            healthId = healthCodeService.getMapping(account.getHealthId());
        }
        if (healthId == null) {
            healthId = healthCodeService.createMapping(studyId);
            account.setHealthId(healthId);
            updateAccount(account);
        } else {
            account.setHealthId(healthId);    
        }
        return account;
    }
    
    /**
     * Create a token that will be sent via email and used to verify the caller. There are 
     * other algorithms used to create such tokens, but this is sufficiently unique and random 
     * that it should work.
     */
    protected String createTimeLimitedToken() {
        return BridgeUtils.generateGuid().replaceAll("-", "");
    }
    
    protected List<SubpopulationGuid> getSubpopulationGuids(StudyIdentifier studyId) {
        return subpopService.getSubpopulations(studyId)
                .stream()
                .map(Subpopulation::getGuid)
                .collect(BridgeCollectors.toImmutableList());
    }    
}
