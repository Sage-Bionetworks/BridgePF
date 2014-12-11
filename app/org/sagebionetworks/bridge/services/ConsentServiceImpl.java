package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.stormpath.sdk.account.Account;

public class ConsentServiceImpl implements ConsentService, ApplicationEventPublisherAware {

    private static final Logger logger = LoggerFactory.getLogger(ConsentServiceImpl.class);

    private static final int TWENTY_FOUR_HOURS = (24 * 60 * 60);

    private AuthenticationService authService;
    private JedisStringOps stringOps = new JedisStringOps();
    private AccountEncryptionService accountEncryptionService;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;
    private ApplicationEventPublisher publisher;

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
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public ConsentSignature getConsentSignature(final User caller, final Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        final StudyConsent consent = studyConsentDao.getConsent(study.getIdentifier());
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        try {
            Account account = authService.getAccount(caller.getEmail());
            ConsentSignature consentSignature = accountEncryptionService.getConsentSignature(study, account);
            if (consentSignature != null) {
                return consentSignature;
            }
        } catch (Throwable e) {
            logger.info("Consent signature not in Stormpath. Fall back to dynamo.");
        }
        ConsentSignature consentSignature = userConsentDao.getConsentSignature(caller.getHealthCode(), consent);
        return consentSignature;
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

        final StudyConsent studyConsent = studyConsentDao.getConsent(study.getIdentifier());

        incrementStudyEnrollment(study);
        try {
            userConsentDao.giveConsent(healthCode, studyConsent);
        } catch (Throwable e) {
            decrementStudyEnrollment(study);
            throw e;
        }

        try {
            accountEncryptionService.putConsentSignature(study, account, consentSignature);
        } catch (Throwable e) {
            logger.error("Failed to write consent signature to Stormpath", e);
        }

        publisher.publishEvent(new UserEnrolledEvent(caller, study));
        if (sendEmail) {
            sendMailService.sendConsentAgreement(caller, consentSignature, studyConsent);
        }

        caller.setConsent(true);
        caller.setHealthCode(healthCode);
        return caller;
    }

    @Override
    public boolean hasUserConsentedToResearch(User caller, Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        final String healthCode = caller.getHealthCode();
        List<StudyConsent> consents = studyConsentDao.getConsents(study.getIdentifier());
        for (StudyConsent consent : consents) {
            if (userConsentDao.hasConsented(healthCode, consent)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public User withdrawConsent(User caller, Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        String healthCode = caller.getHealthCode();
        if (userConsentDao.withdrawConsent(healthCode, study.getIdentifier())) {
            decrementStudyEnrollment(study);
            publisher.publishEvent(new UserUnenrolledEvent(caller, study));
            caller.setConsent(false);
        }

        Account account = authService.getAccount(caller.getEmail());
        accountEncryptionService.removeConsentSignature(study, account);

        return caller;
    }

    @Override
    public void emailConsentAgreement(final User caller, final Study study) {
        checkNotNull(caller, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        final StudyConsent consent = studyConsentDao.getConsent(study.getIdentifier());
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }

        final ConsentSignature consentSignature = getConsentSignature(caller, study);
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
        String countString = stringOps.get(key).execute();
        if (countString == null) {
            // This is expensive but don't lock, it's better to do it twice slowly, than to throw an exception here.
            count = userConsentDao.getNumberOfParticipants(study.getIdentifier());
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
        stringOps.increment(key).execute();
    }

    @Override
    public void decrementStudyEnrollment(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        String count = stringOps.get(key).execute();
        if (count != null && Long.parseLong(count) > 0) {
            stringOps.decrement(key).execute();    
        }
    }
}
