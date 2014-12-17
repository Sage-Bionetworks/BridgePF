package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.group.Group;

public class UserAdminServiceImpl implements UserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    private AuthenticationServiceImpl authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyService studyService;
    private DistributedLockDao lockDao;

    public void setAuthenticationService(AuthenticationServiceImpl authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    @Override
    public UserSession createUser(SignUp signUp, Study study, boolean signUserIn, boolean consentUser)
            throws BridgeServiceException {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(signUp.getEmail(), "Sign up email cannot be null");

        authenticationService.signUp(signUp, study, false);

        SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
        UserSession newUserSession = null;
        try {
            newUserSession = authenticationService.signIn(study, signIn);
        } catch (ConsentRequiredException e) {
            newUserSession = e.getUserSession();
            if (consentUser) {
                String sig = String.format("[Signature for %s]", signUp.getEmail());;
                ConsentSignature consent = ConsentSignature.create(sig, "1989-08-19", null, null);
                consentService.consentToResearch(newUserSession.getUser(), consent, study, false);
            }
        }
        if (!signUserIn) {
            authenticationService.signOut(newUserSession.getSessionToken());
            newUserSession = null;
        }
        return newUserSession;
    }

    @Override
    public void deleteUser(String userEmail) throws BridgeServiceException {
        checkNotNull(userEmail, "User email cannot be null");

        int retryCount = 0;
        boolean shouldRetry = true;
        while (shouldRetry) {
            boolean deleted = deleteUserAttempt(userEmail);
            if (deleted) {
                return;
            }
            shouldRetry = retryCount < 5;
            retryCount++;
            try {
                Thread.sleep(100 * 2 ^ retryCount);
            } catch(InterruptedException ie) {
                throw new BridgeServiceException(ie);
            }
        }
    }

    @Override
    public void deleteAllUsers(Group group) throws BridgeServiceException {
        AccountList accounts = group.getAccounts();
        for (Account account : accounts) {
            deleteUser(account.getEmail());
        }
    }

    private boolean deleteUserAttempt(String userEmail) {
        String key = RedisKey.USER_LOCK.getRedisKey(userEmail);
        String lock = null;
        boolean success = false;
        try {
            lock = lockDao.acquireLock(User.class, key);

            Account account = authenticationService.getAccount(userEmail);
            if (account != null) {
                for (Study study : studyService.getStudies()) {
                    System.out.println("STUDY NAME: " + study.getName());
                    User user = authenticationService.getSessionFromAccount(study, account).getUser();
                    deleteUserInStudy(study, account, user);
                }
                account.delete();
            }
            success = authenticationService.getAccount(userEmail) == null ? true : false;
        } catch(Throwable t) {
            success = false;
        } finally {
            lockDao.releaseLock(User.class, key, lock);
        }
        return success;
    }

    private boolean deleteUserInStudy(Study study, Account account, User user) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(account);
        checkNotNull(user);

        try {
            consentService.withdrawConsent(user, study);
            removeAllHealthDataRecords(study, user);
            //String healthCode = user.getHealthCode();
            //optionsService.deleteAllParticipantOptions(healthCode);
            return true;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    private void removeAllHealthDataRecords(Study study, User user) throws BridgeServiceException {
        // This user may have never consented to research. Ignore if that's the case.
        for (String trackerId : study.getTrackers()) {
            Tracker tracker = studyService.getTrackerByIdentifier(trackerId);
            if (tracker != null) { // this happens with some tests
                HealthDataKey key = new HealthDataKey(study, tracker, user);
                healthDataService.deleteHealthDataRecords(key);
            }
        }
    }

}
