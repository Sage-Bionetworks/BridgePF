package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsentServiceImpl implements ConsentService {

    private static final int TWENTY_FOUR_HOURS = (24 * 60 * 60);

    private AccountDao accountDao;
    private JedisOps jedisOps;
    private ParticipantOptionsService optionsService;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private UserConsentDao userConsentDao;
    private ActivityEventService activityEventService;
    private String consentTemplate;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.consentTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    
    @Autowired
    public final void setStringOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }
    @Resource(name="stormpathAccountDao")
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    public final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    @Autowired
    public final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    @Autowired
    public final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Override
    public ConsentSignature getConsentSignature(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        Account account = accountDao.getAccount(study, user.getEmail());
        ConsentSignature signature = account.getActiveConsentSignature();
        if (signature != null) {
            return signature;
        }
        throw new EntityNotFoundException(ConsentSignature.class);
    }

    @Override
    public User consentToResearch(final Study study, final User user, final ConsentSignature consentSignature,
            final SharingScope sharingScope, final boolean sendEmail) {

        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        if (user.doesConsent()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        final StudyConsentView studyConsent = studyConsentService.getActiveConsent(study);
        final Account account = accountDao.getAccount(study, user.getEmail());

        // Throws exception if we have exceeded enrollment limit.
        incrementStudyEnrollment(study);
        
        account.getConsentSignatures().add(consentSignature);
        accountDao.updateAccount(study, account);
        
        UserConsent userConsent = null;
        try {
            userConsent = userConsentDao.giveConsent(user.getHealthCode(), studyConsent.getStudyConsent(),
                    consentSignature.getSignedOn());
        } catch (Throwable e) {
            // If we can't save consent record, decrement and remove the signature before rethrowing
            decrementStudyEnrollment(study);
            int len = account.getConsentSignatures().size();
            account.getConsentSignatures().remove(len-1);
            accountDao.updateAccount(study, account);
            throw e;
        }
        
        // Save supplemental records, fire events, etc.
        if (userConsent != null){
            activityEventService.publishEnrollmentEvent(user.getHealthCode(), userConsent);
        }
        optionsService.setOption(study, user.getHealthCode(), sharingScope);
        if (sendEmail) {
            MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, user, 
                consentSignature, sharingScope, studyConsentService, consentTemplate);

            sendMailService.sendEmail(consentEmail);
        }
        user.setConsent(true);
        return user;
    }

    @Override
    public boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        return userConsentDao.hasConsented(user.getHealthCode(), studyIdentifier);
    }

    @Override
    public boolean hasUserSignedActiveConsent(StudyIdentifier studyIdentifier, User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "studyIdentifier");

        UserConsent userConsent = userConsentDao.getActiveUserConsent(user.getHealthCode(), studyIdentifier);
        StudyConsentView mostRecentConsent = studyConsentService.getActiveConsent(studyIdentifier);

        if (mostRecentConsent != null && userConsent != null) {
            return userConsent.getConsentCreatedOn() == mostRecentConsent.getCreatedOn();
        } else {
            return false;
        }
    }

    @Override
    public void withdrawConsent(Study study, User user, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(withdrawal, Validate.CANNOT_BE_NULL, "withdrawal");
        checkArgument(withdrewOn > 0L, "withdrewOn not a valid timestamp");
        
        Account account = accountDao.getAccount(study, user.getEmail());
        
        ConsentSignature active = account.getActiveConsentSignature();
        ConsentSignature withdrawn = new ConsentSignature.Builder()
            .withConsentSignature(active)
            .withWithdrewOn(withdrewOn).build();
        int index = account.getConsentSignatures().indexOf(active); // should be length-1
        
        account.getConsentSignatures().set(index, withdrawn);
        accountDao.updateAccount(study, account);

        try {
            userConsentDao.withdrawConsent(user.getHealthCode(), study, withdrewOn);    
        } catch(Exception e) {
            // Could not record the consent, compensate and rethrow the exception
            account.getConsentSignatures().set(index, active);
            accountDao.updateAccount(study, account);
            throw e;
        }
        decrementStudyEnrollment(study);

        optionsService.setOption(study, user.getHealthCode(), SharingScope.NO_SHARING);
        
        String externalId = optionsService.getOption(user.getHealthCode(), ParticipantOption.EXTERNAL_IDENTIFIER);
        MimeTypeEmailProvider consentEmail = new WithdrawConsentEmailProvider(study, externalId, user, withdrawal, withdrewOn);
        sendMailService.sendEmail(consentEmail);
        
        user.setConsent(false);
    }
    
    @Override
    public List<UserConsentHistory> getUserConsentHistory(Study study, User user) {
        Account account = accountDao.getAccount(study, user.getEmail());
        
        return account.getConsentSignatures().stream().map(signature -> {
            UserConsent consent = userConsentDao.getUserConsent(
                    user.getHealthCode(), study, signature.getSignedOn());
            boolean hasSignedActiveConsent = hasUserSignedActiveConsent(study, user);
            
            UserConsentHistory.Builder builder = new UserConsentHistory.Builder();
            builder.withName(signature.getName())
                .withStudyIdentifier(study.getIdentifier())
                .withBirthdate(signature.getBirthdate())
                .withImageData(signature.getImageData())
                .withImageMimeType(signature.getImageMimeType())
                .withSignedOn(signature.getSignedOn())
                .withHealthCode(user.getHealthCode())
                .withWithdrewOn(consent.getWithdrewOn())
                .withConsentCreatedOn(consent.getConsentCreatedOn())
                .withHasSignedActiveConsent(hasSignedActiveConsent);
            return builder.build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public void deleteAllConsents(Study study, User user) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");

        userConsentDao.deleteAllConsents(user.getHealthCode(), study.getStudyIdentifier());
    }
    
    @Override
    public void emailConsentAgreement(final Study study, final User user) {
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkNotNull(study, Validate.CANNOT_BE_NULL, "studyIdentifier");

        final StudyConsentView consent = studyConsentService.getActiveConsent(study);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }

        final ConsentSignature consentSignature = getConsentSignature(study, user);
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }

        final SharingScope sharingScope = optionsService.getSharingScope(user.getHealthCode());
        MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(
            study, user, consentSignature, sharingScope, studyConsentService, consentTemplate);
        sendMailService.sendEmail(consentEmail);
    }

    @Override
    public boolean isStudyAtEnrollmentLimit(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return false;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());

        long count = Long.MAX_VALUE;
        String countString = jedisOps.get(key);
        if (countString == null) {
            // This is expensive but don't lock, it's better to do it twice slowly, than to throw an exception here.
            count = userConsentDao.getNumberOfParticipants(study.getStudyIdentifier());
            jedisOps.setex(key, TWENTY_FOUR_HOURS, Long.toString(count));
        } else {
            count = Long.parseLong(countString);
        }
        return (count >= study.getMaxNumOfParticipants());
    }

    void incrementStudyEnrollment(Study study) throws StudyLimitExceededException {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        if (isStudyAtEnrollmentLimit(study)) {
            throw new StudyLimitExceededException(study);
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        jedisOps.incr(key);
    }

    void decrementStudyEnrollment(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        String count = jedisOps.get(key);
        if (count != null && Long.parseLong(count) > 0) {
            jedisOps.decr(key);
        }
    }
}
