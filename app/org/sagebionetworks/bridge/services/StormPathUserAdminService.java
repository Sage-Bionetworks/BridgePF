package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

public class StormPathUserAdminService implements UserAdminService {

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
            Directory directory = getDirectory(study);
            // Search for email and skip creation if it already exists.
            if (userDoesNotExist(directory, signUp.getEmail())) {
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
                ConsentSignature consent = new ConsentSignature("[Signature for " + signUp.getEmail() + "]", "1989-08-19");
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
    public void revokeAllConsentRecords(User user, Study study) throws BridgeServiceException {
        checkNotNull(user, "User cannot be null");
        checkNotNull(study, "Study cannot be null");
        
        consentService.withdrawConsent(user, study);
    }

    @Override
    public void deleteUser(String userEmail) throws BridgeServiceException {
        checkNotNull(userEmail, "User emailcannot be null");
        for (Study study : studyService.getStudies()) {
            deleteUserInStudyWithRetries(userEmail, study);
        }
    }

    @Override
    public void deleteUser(User user) throws BridgeServiceException {
        checkNotNull(user, "User cannot be null");
        for (Study study : studyService.getStudies()) {
            deleteUserInStudyWithRetries(user, study);
        }
    }

    private void deleteUserInStudyWithRetries(String userEmail, Study study) throws BridgeServiceException {
        checkNotNull(userEmail, "User email cannot be null");
        checkNotNull(study, "Study cannot be null");
        Directory directory = getDirectory(study);
        Account account = getUserAccountByEmail(directory, userEmail);
        if (account != null) {
            User user = new User(account);
            deleteUserInStudyWithRetries(user, study);
        }
    }

    private void deleteUserInStudyWithRetries(User user, Study study) throws BridgeServiceException {
        int retryCount = 0;
        boolean shouldRetry = true;
        while (shouldRetry) {
            try {
                deleteUserInStudy(user, study);
                return;
            } catch(ConcurrentModificationException e) {
                shouldRetry = retryCount < 5;
                retryCount++;
                try {
                    Thread.sleep(100 * 2 ^ retryCount);
                } catch(InterruptedException ie) {
                    throw new BridgeServiceException(ie);
                }
            }
        }
    }

    private void deleteUserInStudy(User user, Study study) throws BridgeServiceException {
        checkNotNull(user, "User cannot be null");
        checkNotNull(study, "Study cannot be null");
        final String lock = study.getKey() + ":" + user.getId();
        String uuid = null;
        try {
            uuid = lockDao.createLock(User.class, lock);
            // Verify the user exists before doing this work. Otherwise, it just throws errors.
            Directory directory = getDirectory(study);
            Account account = getUserAccountByEmail(directory, user.getEmail());
            if (account != null) {
                revokeAllConsentRecords(user, study);
                removeParticipantOptions(user);
                removeAllHealthDataRecords(user, study);
                removeHealthCodeAndIdMappings(user);
                deleteUserAccount(study, user.getEmail());
            }
        } finally {
            if (uuid != null) {
                lockDao.releaseLock(User.class, lock, uuid);
            }
        }
    }

    private void removeParticipantOptions(User user) {
        String healthCode = user.getHealthCode();
        if (healthCode != null) {
            optionsService.deleteAllParticipantOptions(healthCode);    
        }
    }
    
    private void removeHealthCodeAndIdMappings(User user) {
        String healthCode = user.getHealthCode();
        healthIdDao.deleteMapping(healthCode);
        healthCodeDao.deleteCode(healthCode);
    }
    
    private void removeAllHealthDataRecords(User user, Study userStudy) throws BridgeServiceException {
        // This user may have never consented to research. Ignore if that's the case.
        if (user.getHealthCode() != null) {
            List<Tracker> trackers = userStudy.getTrackers();
            HealthDataKey key = null;
            for (Tracker tracker : trackers) {
                key = new HealthDataKey(userStudy, tracker, user);
                List<HealthDataRecord> records = healthDataService.getAllHealthData(key);
                for (HealthDataRecord record : records) {
                    healthDataService.deleteHealthDataRecord(key, record.getGuid());
                }
            }
        }
    }

    private void deleteUserAccount(Study userStudy, String userEmail) throws BridgeServiceException {
        if (StringUtils.isBlank(userEmail)) {
            throw new BadRequestException("User email cannot be blank");
        }
        try {
            Directory directory = getDirectory(userStudy);
            Account account = getUserAccountByEmail(directory, userEmail);
            if (account != null) {
                account.delete();
            }
        } catch (Exception e) {
            throw new BridgeServiceException(e);
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
}
