package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

import controllers.StudyControllerService;

public class StormPathUserAdminService implements UserAdminService {

    private AuthenticationService authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyControllerService studyControllerService;
    private CacheProvider cacheProvider;
    private Client stormpathClient;
    private UserLockDao userLockDao;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }
    
    public void setUserLockDao(UserLockDao userLockDao) {
        this.userLockDao = userLockDao;
    }

    @Override
    public UserSession createUser(String adminSessionToken, Study userStudy, SignUp signUp, boolean signUserIn,
            boolean consentUser) throws BridgeServiceException {
        assertAdminUser(adminSessionToken);
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
                directory.createAccount(account, false); // suppress email
                                                         // message
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
    public void revokeAllConsentRecords(String adminSessionToken, String userSessionToken, Study userStudy)
            throws BridgeServiceException {
        assertAdminUser(adminSessionToken);
        consentService.withdrawConsent(userSessionToken, userStudy);
    }

    @Override
    public void deleteUser(String adminSessionToken, String userSessionToken, Study userStudy)
            throws BridgeServiceException {
        assertAdminUser(adminSessionToken);
        String stormpathID = null;
        String uuid = null;
        try {
            stormpathID = authenticationService.getSession(userSessionToken).getUser().getStormpathID();
            uuid = userLockDao.createLock(stormpathID);
            
            revokeAllConsentRecords(adminSessionToken, userSessionToken, userStudy);
            removeAllHealthDataRecords(adminSessionToken, userSessionToken, userStudy);

            String userEmail = authenticationService.getSession(userSessionToken).getUser().getEmail();
            deleteUserAccount(adminSessionToken, userStudy, userEmail);
            authenticationService.signOut(userSessionToken);
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (stormpathID != null && uuid != null) {
                userLockDao.releaseLock(stormpathID, uuid);
            }
        }
    }

    @Override
    public void deleteUserGlobal(String adminSessionToken, String userSessionToken) throws BridgeServiceException {
        assertAdminUser(adminSessionToken);
        for (Study study : studyControllerService.getStudies()) {
            deleteUser(adminSessionToken, userSessionToken, study);
        }
    }

    private void removeAllHealthDataRecords(String adminSessionToken, String userSessionToken, Study userStudy)
            throws BridgeServiceException {
        assertAdminUser(adminSessionToken);

        List<Tracker> trackers = userStudy.getTrackers();
        HealthDataKey key = null;
        for (Tracker tracker : trackers) {
            key = new HealthDataKey(userStudy, tracker, userSessionToken);
            List<HealthDataRecord> records = healthDataService.getAllHealthData(key);
            for (HealthDataRecord record : records) {
                healthDataService.deleteHealthDataRecord(key, record.getRecordId());
            }
        }
    }

    private void deleteUserAccount(String adminSessionToken, Study userStudy, String userEmail) {
        assertAdminUser(adminSessionToken);
        try {
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, userEmail);
            if (account != null) {
                account.delete();
            }
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
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
