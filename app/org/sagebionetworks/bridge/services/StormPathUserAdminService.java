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
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.sagebionetworks.bridge.validators.SignUpValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

public class StormPathUserAdminService implements UserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(StormPathUserAdminService.class);
    
    private AuthenticationServiceImpl authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyService studyService;
    private Client stormpathClient;
    
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

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    @Override
    public UserSession createUser(SignUp signUp, Study study, boolean signUserIn, boolean consentUser)
            throws BridgeServiceException {
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(study, "Study cannot be null");
        
        SignUpValidator validator = new SignUpValidator(authenticationService);
        Validate.entityThrowingException(validator, signUp);
        
        try {
            Application app = StormpathFactory.getStormpathApplication(stormpathClient);
            // Search for email and skip creation if it already exists.
            if (userDoesNotExist(app, signUp.getEmail())) {
                authenticationService.signUp(signUp, study, false);
            }
        } catch (Throwable t) {
            throw new BridgeServiceException(t);
        }
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

                // Now, sign in again so you get the consented user into the session
                authenticationService.signOut(newUserSession.getSessionToken());
                newUserSession = authenticationService.signIn(study, signIn);
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
    
    private boolean deleteUserAttempt(String userEmail) {
        String key = RedisKey.USER_LOCK.getRedisKey(userEmail);
        String lock = null;
        try {
            lock = lockDao.acquireLock(User.class, key);
            
            Application app = StormpathFactory.getStormpathApplication(stormpathClient);
            Account account = authenticationService.getAccount(userEmail);
            if (account != null) {
                for (Study study : studyService.getStudies()) {
                    User user = authenticationService.getSessionFromAccount(study, account).getUser();
                    deleteUserInStudy(study, account, user);    
                }
                account.delete();
            }
            return true;
        } catch(Throwable t) {
            return false;
        } finally {
            lockDao.releaseLock(User.class, key, lock);
        }
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
            
            HealthDataKey key = new HealthDataKey(study, tracker, user);
            healthDataService.deleteHealthDataRecords(key);
        }
    }

    private boolean userDoesNotExist(Application app, String email) {
        return (authenticationService.getAccount(email) == null);
    }

}
