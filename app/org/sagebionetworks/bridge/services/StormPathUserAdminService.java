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
    public UserSession createUser(String sessionToken, Study userStudy, SignUp signUp, boolean signUserIn,
            boolean consentUser) throws BridgeServiceException {
        assertAdminUser(sessionToken);
        try {
            Directory directory = getDirectory(userStudy);

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
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        UserSession newUserSession = null;
        try {
            SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
            newUserSession = authenticationService.signIn(userStudy, signIn);
        } catch (ConsentRequiredException e) {
            if (consentUser) {
                ResearchConsent consent = new ResearchConsent("Test Signature", "1989-08-19");
                newUserSession = consentService.consentToResearch(e.getUserSession().getSessionToken(), consent,
                        userStudy, false);
            } else {
                return newUserSession;
            }
        }
        if (!signUserIn) {
            authenticationService.signOut(newUserSession.getSessionToken());
        }
        return newUserSession;
    }

    @Override
    public void revokeAllConsentRecords(String sessionToken, Study userStudy, String userEmail)
            throws BridgeServiceException {
        assertAdminUser(sessionToken);

        // Prior to Eric's refactoring, this just involves removing the record
        // from stormpath.
        // Eventually we probably want to clean up some DynamoDB records as
        // well.
        Directory directory = getDirectory(userStudy);
        Account account = getUserAccountByEmail(directory, userEmail);

        CustomData customData = account.getCustomData();
        String key = userStudy.getKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
        customData.remove(key);
        customData.save();
    }

    @Override
    public void deleteUser(String sessionToken, Study userStudy, String userEmail) throws BridgeServiceException {
        assertAdminUser(sessionToken);
        try {
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, userEmail);
            if (account != null) {
                account.delete();
            }
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Directory getDirectory(Study userStudy) {
        return stormpathClient.getResource(userStudy.getStormpathDirectoryHref(), Directory.class);
    }

    private boolean userDoesNotExist(Directory directory, String email) {
        return (getUserAccountByEmail(directory, email) == null);
    }

    private Account getUserAccountByEmail(Directory directory, String email) {
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(email));
        AccountList accounts = directory.getAccounts(criteria);
        return (accounts.iterator().hasNext()) ? accounts.iterator().next() : null;
    }

    private void assertAdminUser(String sessionToken) throws BridgeServiceException {
        UserSession session = cacheProvider.getUserSession(sessionToken);
        Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);

        if (!account.isMemberOfGroup(BridgeConstants.ADMIN_GROUP)) {
            throw new BridgeServiceException("Requires admin user", HttpStatus.SC_FORBIDDEN);
        }

    }

}
