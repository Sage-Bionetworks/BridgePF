package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.ServiceUnavailableException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.account.VerificationEmailRequest;
import com.stormpath.sdk.account.VerificationEmailRequestBuilder;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupMembership;
import com.stormpath.sdk.impl.resource.AbstractResource;
import com.stormpath.sdk.resource.ResourceException;

@Component("stormpathAccountDao")
public class StormpathAccountDao implements AccountDao {

    private static Logger logger = LoggerFactory.getLogger(StormpathAccountDao.class);

    private Application application;
    private Client client;
    private StudyService studyService;
    private SortedMap<Integer,Encryptor> encryptors = Maps.newTreeMap();

    @Resource(name = "stormpathApplication")
    public void setStormpathApplication(Application application) {
        this.application = application;
    }
    @Resource(name = "stormpathClient")
    public void setStormpathClient(Client client) {
        this.client = client;
    }
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Resource(name="encryptorList")
    public void setEncryptors(List<Encryptor> list) {
        for (Encryptor encryptor : list) {
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
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        return new StormpathAccountIterator(study, encryptors, directory.getAccounts(criteria).iterator());
    }

    @Override
    public Account verifyEmail(StudyIdentifier study, EmailVerification verification) {
        checkNotNull(study);
        checkNotNull(verification);
        
        try {
            com.stormpath.sdk.account.Account acct = client.verifyAccountEmail(verification.getSptoken());
            return (acct == null) ? null : new StormpathAccount(study, acct, encryptors);
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
        application.sendVerificationEmail(request);
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
        checkArgument(isNotBlank(signIn.getUsername()));
        checkArgument(isNotBlank(signIn.getPassword()));
        
        try {
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            
            UsernamePasswordRequest request = new UsernamePasswordRequest(signIn.getUsername(), signIn.getPassword(), directory);
            AuthenticationResult result = application.authenticateAccount(request);
            if (result.getAccount() != null) {
                return new StormpathAccount(study.getStudyIdentifier(), result.getAccount(), encryptors);
            }
        } catch (ResourceException e) {
            rethrowResourceException(e, null);
        }
        throw new BridgeServiceException("Authentication failed");
    }

    @Override
    public Account getAccount(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));

        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        
        AccountList accounts = directory.getAccounts(Accounts.where(Accounts.email().eqIgnoreCase(email))
                .withCustomData().withGroups().withGroupMemberships());
        if (accounts.iterator().hasNext()) {
            com.stormpath.sdk.account.Account acct = accounts.iterator().next();
            return new StormpathAccount(study.getStudyIdentifier(), acct, encryptors);
        }
        return null;
    }
    
    @Override 
    public void signUp(Study study, SignUp signUp, boolean sendEmail) {
        checkNotNull(study);
        checkNotNull(signUp);
        
        com.stormpath.sdk.account.Account acct = client.instantiate(com.stormpath.sdk.account.Account.class);
        Account account = new StormpathAccount(study.getStudyIdentifier(), acct, encryptors);
        account.setUsername(signUp.getUsername());
        account.setEmail(signUp.getEmail());
        account.setFirstName(StormpathAccount.PLACEHOLDER_STRING);
        account.setLastName(StormpathAccount.PLACEHOLDER_STRING);
        acct.setPassword(signUp.getPassword());
        if (signUp.getRoles() != null) {
            account.getRoles().addAll(signUp.getRoles());    
        }
        try {
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            directory.createAccount(acct, sendEmail);
            if (!account.getRoles().isEmpty()) {
                updateGroups(directory, account);
            }
        } catch(ResourceException e) {
            rethrowResourceException(e, account);
        }
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
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            updateGroups(directory, account);
            
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
    public void deleteAccount(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        Account account = getAccount(study, email);
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
        default:
            throw new ServiceUnavailableException(e);
        }
    }
    
    private void updateGroups(Directory directory, Account account) {
        Set<String> roles = Sets.newHashSet(account.getRoles());
        com.stormpath.sdk.account.Account acct = ((StormpathAccount)account).getAccount();
        
        // Remove any memberships that don't match a role
        for (GroupMembership membership : acct.getGroupMemberships()) {
            String groupName = membership.getGroup().getName();
            if (!roles.contains(groupName)) {
                // In membership, but not the current list of roles... remove from memberships
                membership.delete();
            } else {
                roles.remove(groupName);
            }
        }
        // Any roles left over need to be added if the group exists
        for (Group group : directory.getGroups()) {
            String groupName = group.getName();
            if (roles.contains(groupName)) {
                // In roles, but not currently in membership... add to memberships
                acct.addGroup(group);
            }
        }
    }
}
