package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.stormpath.sdk.account.Account;

import org.springframework.beans.factory.annotation.Autowired;

public class ConsentServiceImpl implements ConsentService {

    private static final int TWENTY_FOUR_HOURS = (24 * 60 * 60);

    private AuthenticationService authService;
    private JedisStringOps stringOps;
    private AccountEncryptionService accountEncryptionService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

    @Autowired
    public void setStringOps(JedisStringOps stringOps) {
        this.stringOps = stringOps;
    }

    public void setAuthenticationService(AuthenticationService authService) {
        this.authService = authService;
    }

    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    @Override
    public ConsentSignature getConsentSignature(final User caller, final StudyIdentifier studyIdentifier) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        Account account = authService.getAccount(caller.getEmail());
        ConsentSignature consentSignature = accountEncryptionService.getConsentSignature(studyIdentifier, account);
        if (consentSignature != null) {
            return consentSignature;
        }
        throw new EntityNotFoundException(ConsentSignature.class);
    }

    @Override
    public User consentToResearch(final User caller, final ConsentSignature consentSignature,
            final Study study, final boolean sendEmail) throws BridgeServiceException {

        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        // Both of these are validation and should ideally be in the validator, but that was
        // tied to object creation and deserialization, happening multiple places in the
        // codebase.
        if (caller.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        // Stormpath account
        final Account account = authService.getAccount(caller.getEmail());
        HealthId hid = accountEncryptionService.getHealthCode(study, account);
        if (hid == null) {
            hid = accountEncryptionService.createAndSaveHealthCode(study, account);
        }
        final String healthCode = hid.getCode();

        final StudyConsent studyConsent = studyConsentDao.getConsent(study);

        incrementStudyEnrollment(study);
        try {
            userConsentDao.giveConsent(healthCode, studyConsent);
        } catch (Throwable e) {
            decrementStudyEnrollment(study);
            throw e;
        }

        accountEncryptionService.putConsentSignature(study, account, consentSignature);

        if (sendEmail) {
            sendMailService.sendConsentAgreement(caller, consentSignature, studyConsent);
        }

        caller.setConsent(true);
        caller.setHealthCode(healthCode);
        return caller;
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, StudyIdentifier studyIdentifier) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        return userConsentDao.hasConsented(caller.getHealthCode(), studyIdentifier);
    }

    @Override
    public boolean hasUserSignedMostRecentConsent(User caller, StudyIdentifier studyIdentifier) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        UserConsent userConsent = userConsentDao.getUserConsent(caller.getHealthCode(), studyIdentifier);
        StudyConsent mostRecentConsent = studyConsentDao.getConsent(studyIdentifier);

        if (mostRecentConsent != null && userConsent != null) {
            // If the user signed the StudyConsent after the time the most recent StudyConsent was created, then the
            // user has signed the most recent StudyConsent.
            return userConsent.getSignedOn() > mostRecentConsent.getCreatedOn();
        } else {
            return false;
        }

    }

    @Override
    public User withdrawConsent(User caller, Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        String healthCode = caller.getHealthCode();
        if (userConsentDao.withdrawConsent(healthCode, study)) {
            decrementStudyEnrollment(study);
            caller.setConsent(false);
        }

        Account account = authService.getAccount(caller.getEmail());
        accountEncryptionService.removeConsentSignature(study, account);

        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final StudyIdentifier studyIdentifier) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        final StudyConsent consent = studyConsentDao.getConsent(studyIdentifier);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }

        final ConsentSignature consentSignature = getConsentSignature(caller, studyIdentifier);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        sendMailService.sendConsentAgreement(caller, consentSignature, consent);
    }

    @Override
    public boolean isStudyAtEnrollmentLimit(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return false;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());

        long count = Long.MAX_VALUE;
        String countString = stringOps.get(key);
        if (countString == null) {
            // This is expensive but don't lock, it's better to do it twice slowly, than to throw an exception here.
            count = userConsentDao.getNumberOfParticipants(study.getStudyIdentifier());
            stringOps.setex(key, TWENTY_FOUR_HOURS, Long.toString(count));
        } else {
            count = Long.parseLong(countString);
        }
        return (count >= study.getMaxNumOfParticipants());
    }

    @Override
    public void incrementStudyEnrollment(Study study) throws StudyLimitExceededException {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        if (isStudyAtEnrollmentLimit(study)) {
            throw new StudyLimitExceededException(study);
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        stringOps.increment(key);
    }

    @Override
    public void decrementStudyEnrollment(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        String count = stringOps.get(key);
        if (count != null && Long.parseLong(count) > 0) {
            stringOps.decrement(key);
        }
    }
}
