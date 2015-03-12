package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.httpclient.auth.AuthScope.ANY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.ServiceUnavailableException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        
        Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
        return new DirectoryAccountIterator(directory, study, encryptors);
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
        checkNotNull(email);
        
        // This is painful, it's not in the Java SDK. I hope we can come back to this when it's in their SDK
        // and move it over.
        SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager();
        int status = 202; // The Stormpath resend method returns 202 "Accepted" when successful
        byte[] responseBody = new byte[0];
        try {
            BridgeConfig config = BridgeConfigFactory.getConfig();
            
            String bodyJson = "{\"login\":\""+email.getEmail()+"\"}";
            
            HttpClient client = new HttpClient(manager);
            
            PostMethod post = new PostMethod(this.application.getHref() + "/verificationEmails");
            post.setRequestHeader("Accept", "application/json");
            post.setRequestHeader("Content-Type", "application/json");
            post.setRequestHeader("Bridge-Study", studyIdentifier.getIdentifier());
            post.setRequestEntity(new StringRequestEntity(bodyJson, "application/json", "UTF-8"));

            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
                    config.getStormpathId().trim(), config.getStormpathSecret().trim());
            
            client.getState().setCredentials(ANY, creds);
            client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, Lists.newArrayList(AuthPolicy.DIGEST));
            client.getParams().setAuthenticationPreemptive(true);
            
            status = client.executeMethod(post);
            responseBody = post.getResponseBody();

        } catch(ResourceException e) {
            rethrowResourceException(e, null);
        } catch(Throwable throwable) {
            throw new BridgeServiceException(throwable);
        } finally {
            manager.shutdown();
        }
        // If it *wasn't* a 202, then there should be a JSON message included with the response...
        if (status != 202) {

            // One common response, that the email no longer exists, we have mapped to a 404, so do that 
            // here as well. Otherwise we treat it on the API side as a 503 error, a service unavailable problem.
            JsonNode node = null;
            try {
                node = BridgeObjectMapper.get().readTree(responseBody);    
            } catch(IOException e) {
                throw new BridgeServiceException(e);
            }
            String message = node.get("message").asText();
            if (message.contains("does not match a known resource")) {
                throw new EntityNotFoundException(Account.class);
            }
            throw new ServiceUnavailableException(message);
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
            com.stormpath.sdk.account.Account account = application.verifyPasswordResetToken(passwordReset.getSptoken());
            account.setPassword(passwordReset.getPassword());
            account.save();
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
