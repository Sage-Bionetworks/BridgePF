package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConsentServiceImpl implements ConsentService {

    private static final int TWENTY_FOUR_HOURS = (24 * 60 * 60);

    private AccountDao accountDao;
    private JedisStringOps stringOps;
    private SendMailService sendMailService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

    @Autowired
    public void setStringOps(JedisStringOps stringOps) {
        this.stringOps = stringOps;
    }
    @Resource(name="stormpathAccountDao")
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    @Autowired
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    
    @Override
    public ConsentSignature getConsentSignature(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        Account account = accountDao.getAccount(study, user.getEmail());
        ConsentSignature consentSignature = account.getConsentSignature();
        if (consentSignature != null) {
            return consentSignature;
        }
        throw new EntityNotFoundException(ConsentSignature.class);
    }

    @Override
    public User consentToResearch(final Study study, final User user, final ConsentSignature consentSignature,
            final boolean sendEmail) {

        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        if (user.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        Account account = accountDao.getAccount(study, user.getEmail());

        account.setConsentSignature(consentSignature);
        accountDao.updateAccount(study, account);
        
        final StudyConsent studyConsent = studyConsentDao.getConsent(study);

        incrementStudyEnrollment(study);
        try {
            userConsentDao.giveConsent(user.getHealthCode(), studyConsent);
        } catch (Throwable e) {
            decrementStudyEnrollment(study);
            throw e;
        }

        if (sendEmail) {
            sendMailService.sendConsentAgreement(user, consentSignature, studyConsent);
        }

        user.setConsent(true);
        return user;
    }

    @Override
    public UserConsent getUserConsent(StudyIdentifier studyIdentifier, User user) {
        return userConsentDao.getUserConsent(user.getHealthCode(), studyIdentifier);
    }

    @Override
    public boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        return userConsentDao.hasConsented(user.getHealthCode(), studyIdentifier);
    }

    @Override
    public boolean hasUserSignedMostRecentConsent(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        UserConsent userConsent = userConsentDao.getUserConsent(user.getHealthCode(), studyIdentifier);
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
    public void withdrawConsent(Study study, User user) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        
        if (userConsentDao.withdrawConsent(user.getHealthCode(), study)) {
            decrementStudyEnrollment(study);
            Account account = accountDao.getAccount(study, user.getEmail());
            account.setConsentSignature(null);
            accountDao.updateAccount(study, account);
            user.setConsent(false);
        }
    }

    @Override
    public void emailConsentAgreement(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "studyIdentifier");

        final StudyConsent consent = studyConsentDao.getConsent(study);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }

        final ConsentSignature consentSignature = getConsentSignature(study, user);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        sendMailService.sendConsentAgreement(user, consentSignature, consent);
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
