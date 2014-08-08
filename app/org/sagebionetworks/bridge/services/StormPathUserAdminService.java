package org.sagebionetworks.bridge.services;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;

public class StormPathUserAdminService implements UserAdminService {

    private AuthenticationService authenticationService;
    private ConsentService consentService;
    private CacheProvider cacheProvider;
    private Client stormpathClient;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }

    @Override
    public UserSession createAndSignInUser(String sessionToken, Study userStudy, SignUp signUp, boolean hasConsented)
            throws BridgeServiceException {
        assertAdminUser(sessionToken);
        try {
            Directory directory = stormpathClient.getResource(userStudy.getStormpathDirectoryHref(), Directory.class);
            
            // Search for email and skip creation if it already exists.
            if (userDoesNotExist(directory, signUp.getEmail())) {
                Account account = stormpathClient.instantiate(Account.class);
                account.setGivenName("<EMPTY>");
                account.setSurname("<EMPTY>");
                account.setEmail(signUp.getEmail());
                account.setUsername(signUp.getUsername());
                account.setPassword(signUp.getPassword());
                directory.createAccount(account, false); // suppress email message
            }
        } catch(Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        UserSession newUserSession = null;
        try {
            SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
            newUserSession = authenticationService.signIn(userStudy, signIn);
        } catch(ConsentRequiredException e) {
            if (hasConsented) {
                ResearchConsent consent = new ResearchConsent("Test Signature", "1989-08-19");
                return consentService.give(e.getUserSession().getSessionToken(), consent, userStudy, false);
            } else {
                throw e;
            }
        }
        return newUserSession;
    }

    @Override
    public void revokeAllConsentRecords(String sessionToken, Study study, String targetUserSessionToken, String userHref) throws BridgeServiceException {
        assertAdminUser(sessionToken);
        
        // Prior to Eric's refactoring, this just involves removing the record from stormpath.
        // Eventually we probably want to clean up some DynamoDB records as well.
        Account account = stormpathClient.getResource(userHref, Account.class);

        CustomData customData = account.getCustomData();
        String key = study.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
        customData.remove(key);
        customData.save();

        // Update session, because this user may currently be signed in.
        UserSession targetSession = cacheProvider.getUserSession(targetUserSessionToken);
        targetSession.setConsent(false);
        targetSession.setHealthDataCode(null);
        cacheProvider.setUserSession(targetUserSessionToken, targetSession);
    }

    @Override
    public void deleteUser(String sessionToken, String userHref) throws BridgeServiceException {
        assertAdminUser(sessionToken);
        try {
            stormpathClient.getResource(userHref, Account.class).delete();
        } catch(Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    private boolean userDoesNotExist(Directory directory, String email) {
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(email));
        AccountList accounts = directory.getAccounts(criteria);
        return (!accounts.iterator().hasNext());
    }

    private void assertAdminUser(String sessionToken) throws BridgeServiceException {
        UserSession session = cacheProvider.getUserSession(sessionToken);
        Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);
        
        if (!account.isMemberOfGroup(BridgeConstants.ADMIN_GROUP)) {
            throw new BridgeServiceException("Requires admin user", HttpStatus.SC_FORBIDDEN);
        }
        
    }
    
}
