package org.sagebionetworks.bridge.stormpath;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.STORMPATH_NAME_PLACEHOLDER_STRING;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.ServiceUnavailableException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
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
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.AccountOptions;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.account.VerificationEmailRequest;
import com.stormpath.sdk.account.VerificationEmailRequestBuilder;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.authc.UsernamePasswordRequests;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.impl.resource.AbstractResource;
import com.stormpath.sdk.resource.ResourceException;

@Component("stormpathAccountDao")
public class StormpathAccountDao implements AccountDao {

    private static Logger logger = LoggerFactory.getLogger(StormpathAccountDao.class);

    private Application application;
    private Client client;
    private StudyService studyService;
    private SubpopulationService subpopService;
    private HealthCodeService healthCodeService;
    private SortedMap<Integer, BridgeEncryptor> encryptors = Maps.newTreeMap();

    @Resource(name = "stormpathApplication")
    final void setStormpathApplication(Application application) {
        this.application = application;
    }
    @Resource(name = "stormpathClient")
    final void setStormpathClient(Client client) {
        this.client = client;
    }
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
    @Resource(name="encryptorList")
    final void setEncryptors(List<BridgeEncryptor> list) {
        for (BridgeEncryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
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

        // Otherwise default pagination is 25 records per request (100 is the limit, or we'd go higher).
        // Also eagerly fetch custom data, which we typically examine every time for every user.
        AccountCriteria criteria = Accounts.criteria().limitTo(100).withCustomData().withGroupMemberships();
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        return new StormpathAccountIterator(study.getStudyIdentifier(), directory.getAccounts(criteria).iterator());
    }

    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize, String emailFilter) {
        checkNotNull(study);
        checkArgument(offsetBy >= 0);
        checkArgument(pageSize >= API_MINIMUM_PAGE_SIZE && pageSize <= API_MAXIMUM_PAGE_SIZE);

        // limitTo sets the number of records that will be requested from the server, but the iterator behavior
        // of AccountList is such that it will keep fetching records when you get to the limitTo page size. 
        // To make one request of records, you must stop iterating when you get to limitTo records. Furthermore, 
        // getSize() in the iterator is the total number of records that match the criteria... not the smaller of 
        // either the number of records returned or limitTo (as you might expect in a paging API when you get the 
        // last page of records). Behavior as described by Stormpath in email.
        
        AccountCriteria criteria = Accounts.criteria().limitTo(pageSize).offsetBy(offsetBy).orderByEmail();
        if (isNotBlank(emailFilter)) {
            criteria = criteria.add(Accounts.email().containsIgnoreCase(emailFilter));
        }
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        AccountList accts = directory.getAccounts(criteria);
        
        Iterator<com.stormpath.sdk.account.Account> it = accts.iterator();
        
        List<AccountSummary> results = Lists.newArrayListWithCapacity(pageSize);
        for (int i=0; i < pageSize; i++) {
            if (it.hasNext()) {
                com.stormpath.sdk.account.Account acct = it.next();
                results.add(AccountSummary.create(study.getStudyIdentifier(), acct));
            }
        }
        return new PagedResourceList<AccountSummary>(results, offsetBy, pageSize, accts.getSize())
                .withFilter("emailFilter", emailFilter);
    }
    
    @Override
    public Account verifyEmail(StudyIdentifier study, EmailVerification verification) {
        checkNotNull(study);
        checkNotNull(verification);
        
        try {
            com.stormpath.sdk.account.Account acct = client.verifyAccountEmail(verification.getSptoken());
            return (acct == null) ? null : constructAccount(study, acct);
        } catch(ResourceException e) {
            rethrowResourceException(e, null);
        }
        return null;
    }
    
    @Override
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email) {
        checkNotNull(studyIdentifier);
        checkNotNull(email);
        
        final Study study = studyService.getStudy(studyIdentifier);
        final Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        VerificationEmailRequestBuilder requestBuilder = Applications.verificationEmailBuilder();
        VerificationEmailRequest request = requestBuilder
                .setAccountStore(directory)
                .setLogin(email.getEmail())
                .build();

        try {
            application.sendVerificationEmail(request);
        } catch (ResourceException e) {
            rethrowResourceException(e, null);
        }
    }

    @Override
    public void requestResetPassword(Study study, Email email) {
        checkNotNull(study);
        checkNotNull(email);

        try {
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            application.sendPasswordResetEmail(email.getEmail(), directory);
        } catch (ResourceException e) {
            rethrowResourceException(e, null);
        }
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) {
        checkNotNull(passwordReset);
        
        try {
            application.resetPassword(passwordReset.getSptoken(), passwordReset.getPassword());
        } catch (ResourceException e) {
            rethrowResourceException(e, null);
        }
    }
    
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        checkNotNull(study);
        checkNotNull(signIn);
        checkArgument(isNotBlank(signIn.getEmail()));
        checkArgument(isNotBlank(signIn.getPassword()));
        
        try {
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            
            AuthenticationRequest<?,?> request = UsernamePasswordRequests.builder()
                    .setUsernameOrEmail(signIn.getEmail())
                    .setPassword(signIn.getPassword())
                    .withResponseOptions(UsernamePasswordRequests.options().withAccount())
                    .inAccountStore(directory).build();
            
            AuthenticationResult result = application.authenticateAccount(request);
            com.stormpath.sdk.account.Account acct = result.getAccount();
            if (acct != null) {
                // eagerly fetch remaining data with further calls to Stormpath (these are not retrieved in authentication 
                // call, this has been verified with Stormpath). If we fail to fully initialize the user, we want it to 
                // happen here, not later in the call where we don't expect it.
                acct.getCustomData();
                return constructAccount(study, acct);
            }
        } catch (ResourceException e) {
            rethrowResourceException(e, null);
        }
        throw new BridgeServiceException("Authentication failed");
    }

    @Override
    public Account getAccount(Study study, String identifier) {
        checkNotNull(study);
        checkArgument(isNotBlank(identifier));
        
        String href = BridgeConstants.STORMPATH_ACCOUNT_BASE_HREF+identifier;

        AccountOptions<?> options = Accounts.options();
        options.withCustomData();
        options.withGroups();
        options.withGroupMemberships();
        try {
            com.stormpath.sdk.account.Account acct = client.getResource(href, com.stormpath.sdk.account.Account.class, options);

            // Validate the user is in the correct directory
            Directory directory = acct.getDirectory();
            if (directory.getHref().equals(study.getStormpathHref())) {
                return constructAccount(study, acct);
            }
        } catch(ResourceException e) {
            // In keeping with the email implementation, just return null
            logger.debug("Account ID " + identifier + " not found in Stormpath: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public String getHealthCodeForEmail(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        Account account = getAccountWithEmail(study, email);
        if (account == null) {
            return null;
        }
        return account.getHealthCode();
    }
    
    @Override
    public Account constructAccount(Study study, String email, String password) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        checkArgument(isNotBlank(password));
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);
        
        com.stormpath.sdk.account.Account acct = client.instantiate(com.stormpath.sdk.account.Account.class);
        acct.setEmail(email);
        acct.setUsername(email);
        acct.setGivenName(STORMPATH_NAME_PLACEHOLDER_STRING);
        acct.setSurname(STORMPATH_NAME_PLACEHOLDER_STRING);
        acct.setPassword(password);
        Account account = new StormpathAccount(study.getStudyIdentifier(), subpopGuids, acct, encryptors);
        
        HealthId healthId = healthCodeService.createMapping(study);
        account.setHealthId(healthId);
        
        return account;
    }
    
    @Override
    public void createAccount(Study study, Account account, boolean sendVerifyEmail) {
        checkNotNull(study);
        checkNotNull(account);
        
        com.stormpath.sdk.account.Account acct =((StormpathAccount)account).getAccount();
        try {
            // Appears to be unavoidable to make multiple calls here. You have to create the account 
            // before you can manipulate the groups, you cannot submit them all in one request.
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            acct = directory.createAccount(acct, sendVerifyEmail);
            updateGroups(account);
            ((StormpathAccount)account).setAccount(acct);
        } catch(ResourceException e) {
            rethrowResourceException(e, account);
        }
    }
    
    @Override
    public void updateAccount(Account account) {
        checkNotNull(account);
        
        com.stormpath.sdk.account.Account acct =((StormpathAccount)account).getAccount();
        if (acct == null) {
            throw new BridgeServiceException("Account has not been initialized correctly (use new account methods)");
        }
        try {
            updateGroups(account);
            acct.getCustomData().save();
            // This will throw an exception if the account object has not changed, which it may not have
            // if this call was made simply to persist a change in the groups. To get around this, we dig 
            // into the implementation internals of the account and check the dirty state of the object. 
            // In mock tests we override the method involved to avoid test errors. This was verified to be 
            // an issue as of stormpath 1.0.RC9.
            if (isAccountDirty(acct)) {
                acct.save();
            }
        } catch(ResourceException e) {
            rethrowResourceException(e, account);
        }
    }
    
    /**
     * Factored out so it can be overridden in tests; mocks of the Account cannot be cast to AbstractResource.
     */
    public boolean isAccountDirty(com.stormpath.sdk.account.Account acct) {
        AbstractResource res = (AbstractResource)acct;
        return res.isDirty();
    }

    @Override
    public void deleteAccount(Study study, String id) {
        checkNotNull(study);
        checkArgument(isNotBlank(id));
        
        Account account = getAccount(study, id);
        com.stormpath.sdk.account.Account acct =((StormpathAccount)account).getAccount();
        acct.delete();
    }
    
    /**
     * Construct a StormpathAccount and guarantee that the healthid<->healthCode mapping exists for the account.
     */
    private Account constructAccount(StudyIdentifier studyId, com.stormpath.sdk.account.Account acct) {
        checkNotNull(studyId);
        checkNotNull(acct);
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(studyId);
        StormpathAccount account = new StormpathAccount(studyId, subpopGuids, acct, encryptors);
        HealthId healthId = null;
        if (account.getHealthCode() == null) {
            healthId = healthCodeService.getMapping(account.getHealthId());
        }
        if (healthId == null) {
            healthId = healthCodeService.createMapping(studyId);
            updateAccount(account);
        }
        account.setHealthId(healthId);
        return account;
    }
    
    private Account getAccountWithEmail(Study study, String email) {
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);

        AccountList accounts = directory.getAccounts(Accounts.where(Accounts.email().eqIgnoreCase(email))
                .withCustomData().withGroups().withGroupMemberships());
        if (accounts.iterator().hasNext()) {
            com.stormpath.sdk.account.Account acct = accounts.iterator().next();
            return constructAccount(study, acct);
        }
        return null;
    }
    
    private void rethrowResourceException(ResourceException e, Account account) {
        //logger.info(String.format("Stormpath error: %s: %s", e.getCode(), e.getMessage()));
        switch(e.getCode()) {
        case 2001: // must be unique (email isn't unique)
            throw new EntityAlreadyExistsException(account, "Account already exists.");
        // These are validation errors, like "password doesn't include an upper-case character"
        case 400:
        case 2007:
        case 2008:
            throw new BadRequestException(e.getDeveloperMessage());
        case 404:
        case 7100: // Password is bad. Just return not found in this case.
        case 7102: // Login attempt failed because the Account is not verified. 
        case 7104: // Account not found in the directory
        case 2016: // Property value does not match a known resource. Somehow this equals not found.
            throw new EntityNotFoundException(Account.class);
        case 7101:
            // Account is disabled for administrative reasons. This throws 423 LOCKED (WebDAV, not pure HTTP)
            throw new BridgeServiceException("Account disabled, please contact user support", HttpStatus.SC_LOCKED);
        default:
            throw new ServiceUnavailableException(e);
        }
    }

    private void updateGroups(Account account) {
        // new groups, defined by the passed in Account obj
        Set<String> newGroupSet = new HashSet<>();
        //noinspection Convert2streamapi
        for (Roles role : account.getRoles()) {
            newGroupSet.add(role.name().toLowerCase());
        }

        // old groups, stored in Stormpath
        com.stormpath.sdk.account.Account acct = ((StormpathAccount)account).getAccount();
        Set<String> oldGroupSet = new HashSet<>();
        if (acct.getGroups() != null) {
            for (Group group : acct.getGroups()) {
                oldGroupSet.add(group.getName());
            }
        }

        // added groups = new groups - old groups
        Set<String> addedGroupSet = Sets.difference(newGroupSet, oldGroupSet);
        addedGroupSet.forEach(acct::addGroup);

        // removed groups = old groups - new groups
        Set<String> removedGroupSet = Sets.difference(oldGroupSet, newGroupSet);
        removedGroupSet.forEach(acct::removeGroup);
    }
    
    private List<SubpopulationGuid> getSubpopulationGuids(StudyIdentifier studyId) {
        return subpopService.getSubpopulations(studyId)
                .stream()
                .map(Subpopulation::getGuid)
                .collect(BridgeCollectors.toImmutableList());
    }
}
