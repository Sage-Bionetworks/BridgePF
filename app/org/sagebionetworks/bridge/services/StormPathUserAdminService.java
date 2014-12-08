package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Validator;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

public class StormPathUserAdminService implements UserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(StormPathUserAdminService.class);
    
    private AuthenticationService authenticationService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private StudyService studyService;
    private Client stormpathClient;
    private ParticipantOptionsService optionsService;
    private DistributedLockDao lockDao;
    private Validator validator;
    
    private HealthIdDao healthIdDao;
    private HealthCodeDao healthCodeDao;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
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

    public void setValidator(Validator validator) {
        this.validator = validator;
    }
    
    public void setHealthIdDao(HealthIdDao healthIdDao) {
        this.healthIdDao = healthIdDao;
    }
    
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    @Override
    public UserSession createUser(SignUp signUp, Study study, boolean signUserIn, boolean consentUser)
            throws BridgeServiceException {
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(study, "Study cannot be null");
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
                ConsentSignature consent = ConsentSignature.create("[Signature for " + signUp.getEmail() + "]",
                        "1989-08-19", null, null);
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
        
        String key = RedisKey.USER_LOCK.getRedisKey(userEmail);
        String lock = null;
        try {
            lock = lockDao.acquireLock(User.class, key);
            
            Application app = StormpathFactory.getStormpathApplication(stormpathClient);
            Account account = getUserAccountByEmail(app, userEmail);
            if (account != null) {
                for (Study study : studyService.getStudies()) {
                    User user = authenticationService.createSessionFromAccount(study, account).getUser();
                    if (user.getHealthCode() != null) {
                        deleteUserInStudy(study, account, user);    
                    }
                }
                account.delete();
            }
        } finally {
            lockDao.releaseLock(User.class, key, lock);
        }
    }
    
    private boolean deleteUserInStudy(Study study, Account account, User user) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(account);
        checkNotNull(user);
        
        try {
            String healthCode = user.getHealthCode();
            consentService.withdrawConsent(user, study);
            removeAllHealthDataRecords(study, user);
            // This might be lightning fast, but we don't have to do any of the following, really.
            // See if it shaves any time off.
            optionsService.deleteAllParticipantOptions(healthCode);
            healthIdDao.deleteMapping(healthCode);
            healthCodeDao.deleteCode(healthCode);
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
            List<HealthDataRecord> records = healthDataService.getAllHealthData(key);
            for (HealthDataRecord record : records) {
                healthDataService.deleteHealthDataRecord(key, record.getGuid());
            }
        }
    }

    private boolean userDoesNotExist(Application app, String email) {
        return (getUserAccountByEmail(app, email) == null);
    }

    private Account getUserAccountByEmail(Application app, String email) {
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(email));
        AccountList accounts = app.getAccounts(criteria);
        return (accounts.iterator().hasNext()) ? accounts.iterator().next() : null;
    }
}
