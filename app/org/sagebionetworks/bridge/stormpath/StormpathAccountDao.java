package org.sagebionetworks.bridge.stormpath;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.stormpath.StormpathAccount.PLACEHOLDER_STRING;

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
import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
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
    public final void setStormpathApplication(Application application) {
        this.application = application;
    }
    @Resource(name = "stormpathClient")
    public final void setStormpathClient(Client client) {
        this.client = client;
    }
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Autowired
    final void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    @Resource(name="encryptorList")
    public final void setEncryptors(List<BridgeEncryptor> list) {
        for (BridgeEncryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
        }
    }

    @Override
    public Iterator<Account> getAllAccounts() {
        Iterator<Account> combinedIterator = null;
        for (Study study : studyService.getStudies()) {
            Iterator<Account> studyIterator = getStudyAccounts(study);
            if (combinedIterator ==  null) {
                combinedIterator = studyIterator;
            } else {
                combinedIterator = Iterators.concat(combinedIterator, studyIterator);    
            }
        }
        return combinedIterator;
    }

    @Override
    public Iterator<Account> getStudyAccounts(Study study) {
        checkNotNull(study);

        // Otherwise default pagination is 25 records per request (100 is the limit, or we'd go higher).
        // Also eagerly fetch custom data, which we typically examine every time for every user.
        AccountCriteria criteria = Accounts.criteria().limitTo(100).withCustomData().withGroupMemberships();
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        return new StormpathAccountIterator(study, subpopGuids, encryptors, directory.getAccounts(criteria).iterator());
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
                java.util.Date javaDate = acct.getCreatedAt();
                DateTime createdOn = (javaDate != null) ? new DateTime(javaDate) : null;
                String id = BridgeUtils.getIdFromStormpathHref(acct.getHref());
                
                String firstName = (PLACEHOLDER_STRING.equals(acct.getGivenName())) ? null : acct.getGivenName();
                String lastName = (PLACEHOLDER_STRING.equals(acct.getSurname())) ? null : acct.getSurname();
                
                // This should not trigger further requests to the server (customData, groups, etc.).
                AccountSummary summary = new AccountSummary(firstName, lastName, 
                        acct.getEmail(), id, createdOn, AccountStatus.valueOf(acct.getStatus().name()));
                results.add(summary);
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
            List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);
            
            com.stormpath.sdk.account.Account acct = client.verifyAccountEmail(verification.getSptoken());
            return (acct == null) ? null : new StormpathAccount(study, subpopGuids, acct, encryptors);
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
            List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);
            
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
                return new StormpathAccount(study.getStudyIdentifier(), subpopGuids, acct, encryptors);
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
        
        return getAccountWithId(study, identifier);
    }
    
    @Override
    public String getHealthCodeForEmail(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        Account account = getAccountWithEmail(study, email);
        if (account == null) {
            return null;
        }
        
        // This method is called to update a user's preferences, including requests to no longer 
        // be sent unsolicited email. So even if this user hasn't been assigned a healthCode (I 
        // only recently changed this to create a healthCode when an account is created, and not later), 
        // we need to create one so the preference can be recorded.
        HealthId healthId = healthCodeService.getMapping(account.getHealthId());
        if (healthId == null) {
            healthId = healthCodeService.createMapping(study.getStudyIdentifier());
            account.setHealthId(healthId.getId());
            updateAccount(study, account);
        }
        return healthId.getCode();
    }
    
    private Account getAccountWithEmail(Study study, String email) {
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);

        AccountList accounts = directory.getAccounts(Accounts.where(Accounts.email().eqIgnoreCase(email))
                .withCustomData().withGroups().withGroupMemberships());
        if (accounts.iterator().hasNext()) {
            com.stormpath.sdk.account.Account acct = accounts.iterator().next();
            return new StormpathAccount(study.getStudyIdentifier(), subpopGuids, acct, encryptors);
        }
        return null;
    }
    
    private Account getAccountWithId(Study study, String id) {
        String href = BridgeConstants.STORMPATH_ACCOUNT_BASE_HREF+id;
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);

        AccountOptions<?> options = Accounts.options();
        options.withCustomData();
        options.withGroups();
        options.withGroupMemberships();
        try {
            com.stormpath.sdk.account.Account acct = client.getResource(href, com.stormpath.sdk.account.Account.class, options);
            
            // Validate the user is in the correct directory
            Directory directory = acct.getDirectory();
            if (directory.getHref().equals(study.getStormpathHref())) {
                return new StormpathAccount(study.getStudyIdentifier(), subpopGuids, acct, encryptors); 
            }
        } catch(ResourceException e) {
            // In keeping with the email implementation, just return null
            logger.debug("Account ID " + id + " not found in Stormpath: " + e.getMessage());
        }
        return null;
    }
    
    @Override 
    public Account signUp(Study study, StudyParticipant participant, boolean sendEmail) {
        checkNotNull(study);
        checkNotNull(participant);
        
        List<SubpopulationGuid> subpopGuids = getSubpopulationGuids(study);
        
        com.stormpath.sdk.account.Account acct = client.instantiate(com.stormpath.sdk.account.Account.class);
        Account account = new StormpathAccount(study.getStudyIdentifier(), subpopGuids, acct, encryptors);
        account.setEmail(participant.getEmail());
        account.setFirstName(StormpathAccount.PLACEHOLDER_STRING);
        account.setLastName(StormpathAccount.PLACEHOLDER_STRING);
        acct.setPassword(participant.getPassword());
        if (participant.getRoles() != null) {
            account.getRoles().addAll(participant.getRoles());
        }
        // Create the healthCode mapping when we create the account. Stop waiting to create it
        HealthId healthId = healthCodeService.createMapping(study);
        account.setHealthId(healthId.getId());
        
        try {
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            directory.createAccount(acct, sendEmail);
            if (!account.getRoles().isEmpty()) {
                updateGroups(account);
            }
        } catch(ResourceException e) {
            rethrowResourceException(e, account);
        }
        return account;
    }
    
    @Override
    public void updateAccount(Study study, Account account) {
        checkNotNull(study);
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
            // into the implementation internals of the account because the Stormpath code is tracking the 
            // dirty state of the object.
            AbstractResource res = (AbstractResource)acct;
            if (res.isDirty()) {
                acct.save();
            }
        } catch(ResourceException e) {
            rethrowResourceException(e, account);
        }
    }

    @Override
    public void deleteAccount(Study study, String id) {
        checkNotNull(study);
        checkArgument(isNotBlank(id));
        
        Account account = getAccount(study, id);
        com.stormpath.sdk.account.Account acct =((StormpathAccount)account).getAccount();
        acct.delete();
    }
    
    private void rethrowResourceException(ResourceException e, Account account) {
        logger.info(String.format("Stormpath error: %s: %s", e.getCode(), e.getMessage()));
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
        for (Group group : acct.getGroups()) {
            oldGroupSet.add(group.getName());
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
