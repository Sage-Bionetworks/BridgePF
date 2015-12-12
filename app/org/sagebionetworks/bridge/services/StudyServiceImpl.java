package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("studyService")
public class StudyServiceImpl implements StudyService {
    
    private static final int TWENTY_FOUR_HOURS = (24*60*60);

    private final Set<String> studyWhitelist = Collections.unmodifiableSet(new HashSet<>(
            BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private JedisOps jedisOps;
    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private DistributedLockDao lockDao;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    private SubpopulationService subpopService;
    private UserConsentDao userConsentDao;

    private String defaultEmailVerificationTemplate;
    private String defaultEmailVerificationTemplateSubject;
    private String defaultResetPasswordTemplate;
    private String defaultResetPasswordTemplateSubject;
    
    @Autowired
    final void setStringOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }
    @Value("classpath:study-defaults/email-verification.txt")
    final void setDefaultEmailVerificationTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:study-defaults/email-verification-subject.txt")
    final void setDefaultEmailVerificationTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:study-defaults/reset-password.txt")
    final void setDefaultPasswordTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:study-defaults/reset-password-subject.txt")
    final void setDefaultPasswordTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Resource(name="uploadCertificateService")
    final void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }
    @Autowired
    final void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    final void setValidator(StudyValidator validator) {
        this.validator = validator;
    }
    @Autowired
    final void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    @Autowired
    final void setDirectoryDao(DirectoryDao directoryDao) {
        this.directoryDao = directoryDao;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Autowired
    final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    
    @Override
    public Study getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }
        return study;
    }
    @Override
    public Study getStudy(StudyIdentifier studyId) {
        checkNotNull(studyId, Validate.CANNOT_BE_NULL, "studyIdentifier");
        
        return getStudy(studyId.getIdentifier());
    }
    @Override
    public List<Study> getStudies() {
        return studyDao.getStudies();
    }
    @Override
    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        setDefaultsIfAbsent(study);
        sanitizeHTML(study);
        Validate.entityThrowingException(validator, study);

        String id = study.getIdentifier();
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, id);

            if (studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            
            subpopService.createDefaultSubpopulation(study);
            
            study.setActive(true);
            study.setStrictUploadValidationEnabled(true);

            String directory = directoryDao.createDirectoryForStudy(study);
            study.setStormpathHref(directory);

            // do not create certs for whitelisted studies (legacy studies)
            if (!studyWhitelist.contains(study.getIdentifier())) {
                uploadCertService.createCmsKeyPair(study.getStudyIdentifier());
            }

            study = studyDao.createStudy(study);
            
            cacheProvider.setStudy(study);
        } finally {
            lockDao.releaseLock(Study.class, id, lockId);
        }
        return study;
    }
    @Override
    public Study updateStudy(Study study, boolean isAdminUpdate) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        sanitizeHTML(study);

        // These cannot be set through the API and will be null here, so they are set on update
        Study originalStudy = studyDao.getStudy(study.getIdentifier());
        study.setStormpathHref(originalStudy.getStormpathHref());
        study.setActive(true);
        // And this cannot be set unless you're an administrator. Regardless of what the 
        // developer set, set these back to the original study.
        if (!isAdminUpdate) {
            study.setMaxNumOfParticipants(originalStudy.getMaxNumOfParticipants());
            study.setHealthCodeExportEnabled(originalStudy.isHealthCodeExportEnabled());
        }
        Validate.entityThrowingException(validator, study);

        // When the version is out of sync in the cache, then an exception is thrown and the study 
        // is not updated in the cache. At least we can delete the study before this, so the next 
        // time it should succeed. Have not figured out why they get out of sync.
        cacheProvider.removeStudy(study.getIdentifier());
        
        // Only update the directory if a relevant aspect of the study has changed.
        if (studyDirectoryHasChanged(originalStudy, study)) {
            directoryDao.updateDirectoryForStudy(study);
        }
        Study updatedStudy = studyDao.updateStudy(study);
        
        cacheProvider.setStudy(updatedStudy);
        
        return updatedStudy;
    }
    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        if (studyWhitelist.contains(identifier)) {
            throw new UnauthorizedException(identifier + " is protected by whitelist.");
        }

        // Verify the study exists before you do this.
        Study existing = getStudy(identifier);
        if (existing == null) {
            throw new EntityNotFoundException(Study.class, "Study '"+identifier+"' not found");
        }
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, identifier);
            studyDao.deleteStudy(existing);
            directoryDao.deleteDirectoryForStudy(existing);
            subpopService.deleteAllSubpopulations(existing.getStudyIdentifier());
            cacheProvider.removeStudy(identifier);
        } finally {
            lockDao.releaseLock(Study.class, identifier, lockId);
        }
    }
    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        Set<String> healthCodes = Sets.newHashSet();
        
        List<Subpopulation> subpops = subpopService.getSubpopulations(studyIdentifier);
        for (Subpopulation subpop : subpops) {
            Set<String> subpopCodes = userConsentDao.getParticipantHealthCodes(subpop);
            healthCodes.addAll(subpopCodes);
        }
        
        return healthCodes.size();
    }
    
    @Override
    public boolean isStudyAtEnrollmentLimit(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return false;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        long count = Long.MAX_VALUE;
        
        // getNumberOfParticipants() is very expensive. Cache this.
        String countString = jedisOps.get(key);
        if (countString == null) {
            count = getNumberOfParticipants(study); 
            jedisOps.setex(key, TWENTY_FOUR_HOURS, Long.toString(count));
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
        jedisOps.incr(key);
    }
    @Override
    public void decrementStudyEnrollment(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        String count = jedisOps.get(key);
        if (count != null && Long.parseLong(count) > 0) {
            jedisOps.decr(key);
        }
    }
    
    /**
     * Has an aspect of the study changed that must be saved as well in the Stormpath directory?
     * @param originalStudy
     * @param study
     * @return true if the password policy or email templates have changed
     */
    private boolean studyDirectoryHasChanged(Study originalStudy, Study study) {
        return (!study.getPasswordPolicy().equals(originalStudy.getPasswordPolicy()) || 
                !study.getVerifyEmailTemplate().equals(originalStudy.getVerifyEmailTemplate()) || 
                !study.getResetPasswordTemplate().equals(originalStudy.getResetPasswordTemplate()));
    }
    
    /**
     * When the password policy or templates are not included, they are set to some sensible default 
     * values. 
     * @param study
     */
    private void setDefaultsIfAbsent(Study study) {
        if (study.getPasswordPolicy() == null) {
            study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }
        study.setVerifyEmailTemplate(fillOutTemplate(study.getVerifyEmailTemplate(),
            defaultEmailVerificationTemplateSubject, defaultEmailVerificationTemplate));
        study.setResetPasswordTemplate(fillOutTemplate(study.getResetPasswordTemplate(),
            defaultResetPasswordTemplateSubject, defaultResetPasswordTemplate));
    }

    /**
     * Partially resolve variables in the templates before saving on Stormpath. If templates are not provided, 
     * then the default templates are used, and we assume these are text only, otherwise this method needs to 
     * change the mime type it sets.
     * @param template
     * @param defaultSubject
     * @param defaultBody
     * @return
     */
    private EmailTemplate fillOutTemplate(EmailTemplate template, String defaultSubject, String defaultBody) {
        if (template == null) {
            template = new EmailTemplate(defaultSubject, defaultBody, MimeType.HTML);
        }
        if (StringUtils.isBlank(template.getSubject())) {
            template = new EmailTemplate(defaultSubject, template.getBody(), template.getMimeType());
        }
        if (StringUtils.isBlank(template.getBody())) {
            template = new EmailTemplate(template.getSubject(), defaultBody, template.getMimeType());
        }
        return template;
    }
    
    /**
     * Email templates can contain HTML. Ensure the subject text has no markup and the markup in the body 
     * is safe for display in web-based email clients and a researcher UI. We clean this up before 
     * validation in case only unacceptable content was in the template. 
     * @param study
     */
    private void sanitizeHTML(Study study) {
        EmailTemplate template = study.getVerifyEmailTemplate();
        study.setVerifyEmailTemplate(sanitizeEmailTemplate(template));
        
        template = study.getResetPasswordTemplate();
        study.setResetPasswordTemplate(sanitizeEmailTemplate(template));
    }
    
    private EmailTemplate sanitizeEmailTemplate(EmailTemplate template) {
        // Skip sanitization if there's no template. This can happen now as we'd rather see an error if the caller
        // doesn't include a template when updating.
        if (template == null) {
            return template;
        }
        String subject = template.getSubject();
        if (StringUtils.isNotBlank(subject)) {
            subject = Jsoup.clean(subject, Whitelist.none());
        }
        String body = template.getBody();
        if (StringUtils.isNotBlank(body)) {
            if (template.getMimeType() == MimeType.TEXT) {
                body = Jsoup.clean(body, Whitelist.none());
            } else {
                body = Jsoup.clean(body, Whitelist.relaxed());
            }
        }
        return new EmailTemplate(subject, body, template.getMimeType());
    }
}
