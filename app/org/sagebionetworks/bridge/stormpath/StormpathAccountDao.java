package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupMembership;
import com.stormpath.sdk.impl.resource.AbstractResource;
import com.stormpath.sdk.resource.ResourceException;

public class StormpathAccountDao implements AccountDao {

    // private static Logger logger = LoggerFactory.getLogger(StormpathAccountDao.class);

    private Client client;
    private StudyService studyService;
    private SortedMap<Integer,Encryptor> encryptors = Maps.newTreeMap();
    
    public void setStormpathClient(Client client) {
        this.client = client;
    }
    
    public void setEncryptors(List<Encryptor> list) {
        for (Encryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
        }
    }
    
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
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
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        return new DirectoryAccountIterator(directory, study, encryptors);
    }
    
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        checkNotNull(study);
        checkNotNull(signIn);
        checkArgument(isNotBlank(signIn.getUsername()));
        checkArgument(isNotBlank(signIn.getPassword()));
        
        try {
            BridgeConfig config = BridgeConfigFactory.getConfig();
            Application application = client.getResource(config.getStormpathApplicationHref().trim(), Application.class);

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
                .withGroups().withGroupMemberships());
        if (!accounts.iterator().hasNext()) {
            throw new EntityNotFoundException(Account.class);
        }
        com.stormpath.sdk.account.Account acct = accounts.iterator().next();
        StormpathAccount account = new StormpathAccount(study.getStudyIdentifier(), acct, encryptors);
        return account;
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
        account.getRoles().addAll(signUp.getRoles());
        acct.setPassword(signUp.getPassword());
        
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
        if (e.getCode() == 2001) { // must be unique (email isn't unique)
            throw new EntityAlreadyExistsException(account);
        } else if (e.getCode() == 7104) { // account not found in the directory
            throw new EntityNotFoundException(Account.class);
        }
        throw new BridgeServiceException(e);
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
