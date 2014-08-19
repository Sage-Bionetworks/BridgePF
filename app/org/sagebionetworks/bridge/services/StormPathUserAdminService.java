package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

import controllers.StudyControllerService;

public class StormPathUserAdminService implements UserAdminService {

    private static Logger logger = LoggerFactory.getLogger(StormPathUserAdminService.class);

    private AuthenticationService authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyControllerService studyControllerService;
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

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }
    
    public void setUserLockDao(UserLockDao userLockDao) {
        this.userLockDao = userLockDao;
    }

    @Override
    public UserSession createUser(User adminUser, SignUp signUp, Study userStudy, boolean signUserIn, boolean consentUser)
            throws BridgeServiceException {
        if (adminUser == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (signUp == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getUsername())) {
            throw new BridgeServiceException("User's username cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getEmail())) {
            throw new BridgeServiceException("User's meail cannot be null", 400);
        } else if (StringUtils.isBlank(signUp.getPassword())) {
            throw new BridgeServiceException("User's password cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        assertAdminUser(adminUser);
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
        SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
        UserSession newUserSession = null;
        try {
            newUserSession = authenticationService.signIn(userStudy, signIn);
        } catch (ConsentRequiredException e) {
            newUserSession = e.getUserSession();
            if (consentUser) {
                ResearchConsent consent = new ResearchConsent("Test Signature", "1989-08-19");
                consentService.consentToResearch(newUserSession.getUser(), consent, userStudy, false);

                // Now, sign in again so you get the consented user into the session
                authenticationService.signOut(newUserSession.getSessionToken());
                newUserSession = authenticationService.signIn(userStudy, signIn);
            }
        }
        if (!signUserIn && newUserSession != null) {
            authenticationService.signOut(newUserSession.getSessionToken());
            newUserSession = null;
        }
        return newUserSession;
    }

    @Override
    public void revokeAllConsentRecords(User caller, User user, Study userStudy) throws BridgeServiceException {
        if (caller == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        assertAdminUser(caller);
        consentService.withdrawConsent(user, userStudy);
    }

    @Override
    public void deleteUser(User caller, User user, Study userStudy) throws BridgeServiceException {
        if (caller == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        assertAdminUser(caller);
        String stormpathID = null;
        String uuid = null;
        try {
            //uuid = userLockDao.createLock(user.getId());
            
            // Verify the user exists before doing this work. Otherwise, it just throws errors.
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, user.getEmail());
            if (account != null) {
                revokeAllConsentRecords(caller, user, userStudy);
                removeAllHealthDataRecords(caller, user, userStudy);
                deleteUserAccount(caller, userStudy, user.getEmail());
            }
        } catch (Throwable t) {
            throw new BridgeServiceException(t, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } /*finally {
            if (stormpathID != null && uuid != null) {
                userLockDao.releaseLock(stormpathID, uuid);
            }
        }*/
    }

    @Override
    public void deleteUserGlobal(User caller, User user) throws BridgeServiceException {
        if (caller == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        }
        assertAdminUser(caller);
        for (Study study : studyControllerService.getStudies()) {
            deleteUser(caller, user, study);
        }
    }

    private void removeAllHealthDataRecords(User caller, User user, Study userStudy)
            throws BridgeServiceException {
        if (caller == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (user == null) {
            throw new BridgeServiceException("User cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        }
        assertAdminUser(caller);

        // This user may have never consented to research. Ignore if that's the case.
        if (user.getHealthDataCode() != null) {
            List<Tracker> trackers = userStudy.getTrackers();
            HealthDataKey key = null;
            for (Tracker tracker : trackers) {
                key = new HealthDataKey(userStudy, tracker, user);
                List<HealthDataRecord> records = healthDataService.getAllHealthData(key);
                for (HealthDataRecord record : records) {
                    healthDataService.deleteHealthDataRecord(key, record.getRecordId());
                }
            }
        }
    }

    private void deleteUserAccount(User caller, Study userStudy, String userEmail) throws BridgeServiceException {
        if (caller == null) {
            throw new BridgeServiceException("Calling admin user cannot be null", 400);
        } else if (userStudy == null) {
            throw new BridgeServiceException("User study cannot be null", 400);
        } else if (StringUtils.isBlank(userEmail)) {
            throw new BridgeServiceException("User email cannot be blank", 400);
        }
        assertAdminUser(caller);
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

    private void assertAdminUser(User user) throws BridgeServiceException {
        logger.info("Admin user: " + user.toString());
        if (!user.getRoles().contains(BridgeConstants.ADMIN_GROUP)) {
            throw new BridgeServiceException("Requires admin user", HttpStatus.SC_FORBIDDEN);
        }
    }

}
