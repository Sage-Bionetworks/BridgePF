package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
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

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.MembershipInvitation;
import org.sagebionetworks.repo.model.MembershipInvtnSubmission;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.util.ModelConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("studyService")
public class StudyService {

    static final String EXPORTER_SYNAPSE_USER_ID = BridgeConfigFactory.getConfig().getExporterSynapseId(); // copy-paste from website

    private final Set<String> studyWhitelist = Collections.unmodifiableSet(new HashSet<>(
            BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    private SubpopulationService subpopService;
    private EmailVerificationService emailVerificationService;
    private SynapseClient synapseClient;

    private String defaultEmailVerificationTemplate;
    private String defaultEmailVerificationTemplateSubject;
    private String defaultResetPasswordTemplate;
    private String defaultResetPasswordTemplateSubject;
    
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
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }
    @Autowired
    @Qualifier("bridgePFSynapseClient")
    public final void setSynapseClient(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }

    private Study getStudy(String identifier, boolean includeDeleted) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }

        if (study != null && !study.isActive() && !includeDeleted) {
            throw new EntityNotFoundException(Study.class, "Study not found.");
        }

        return study;
    }

    // only return active study
    public Study getStudy(String identifier) {
        return getStudy(identifier, false);
    }

    public Study getStudy(StudyIdentifier studyId) {
        checkNotNull(studyId, Validate.CANNOT_BE_NULL, "studyIdentifier");
        
        return getStudy(studyId.getIdentifier());
    }

    public List<Study> getStudies() {
        return studyDao.getStudies();
    }

    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        study.setActive(true);
        study.setStrictUploadValidationEnabled(true);
        study.setEmailVerificationEnabled(true);
        study.getDataGroups().add(BridgeConstants.TEST_USER_GROUP);
        setDefaultsIfAbsent(study);
        sanitizeHTML(study);
        Validate.entityThrowingException(validator, study);

        if (studyDao.doesIdentifierExist(study.getIdentifier())) {
            throw new EntityAlreadyExistsException(study);
        }
        
        subpopService.createDefaultSubpopulation(study);
        
        String directory = directoryDao.createDirectoryForStudy(study);
        study.setStormpathHref(directory);

        // do not create certs for whitelisted studies (legacy studies)
        if (!studyWhitelist.contains(study.getIdentifier())) {
            uploadCertService.createCmsKeyPair(study.getStudyIdentifier());
        }

        study = studyDao.createStudy(study);
        
        emailVerificationService.verifyEmailAddress(study.getSupportEmail());
        
        cacheProvider.setStudy(study);

        return study;
    }

    public Study createSynapseProjectTeam(Long userId, Study study) throws SynapseException {
        // first check if study already has project and team ids
        checkNewEntity(study, study.getSynapseDataAccessTeamId(), "Study already has a team id.");
        checkNewEntity(study, study.getSynapseProjectId(), "Study already has a project id.");

        // then check if the user id exists
        try {
            synapseClient.getUserProfile(userId.toString());
        } catch (SynapseNotFoundException e) {
            throw new BadRequestException("Synapse user Id is invalid.");
        }

        // create synapse project and team
        Team team = new Team();
        team.setName(study.getName().trim().replaceAll("[\\s\\[\\]]", "_") + "AccessTeam");
        Project project = new Project();
        project.setName(study.getName().trim().replaceAll("[\\s\\[\\]]", "_") + "Project");

        Team newTeam = synapseClient.createTeam(team);

        Project newProject = synapseClient.createEntity(project);

        // modify project acl
        org.sagebionetworks.repo.model.AccessControlList acl = synapseClient.getACL(newProject.getId());
        // add exporter as admin
        ResourceAccess toSet = new ResourceAccess();
        toSet.setPrincipalId(Long.parseLong(EXPORTER_SYNAPSE_USER_ID));
        toSet.setAccessType(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
        acl.getResourceAccess().add(toSet);
        // add user as admin
        ResourceAccess toSetUser = new ResourceAccess();
        toSetUser.setPrincipalId(userId); // passed by user as parameter
        toSetUser.setAccessType(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
        acl.getResourceAccess().add(toSetUser);

        synapseClient.updateACL(acl);

        // send invitation to target user for joining new team and grant admin permission to that user
        MembershipInvtnSubmission teamMemberInvitation = new MembershipInvtnSubmission();
        teamMemberInvitation.setInviteeId(userId.toString());
        teamMemberInvitation.setTeamId(newTeam.getId());

        synapseClient.createMembershipInvitation(teamMemberInvitation, null, null);
        synapseClient.setTeamMemberPermissions(newTeam.getId(), userId.toString(), true);

        String newTeamId = newTeam.getId();
        String newProjectId = newProject.getId();

        // finally, update study
        study.setSynapseProjectId(newProjectId);
        study.setSynapseDataAccessTeamId(Long.parseLong(newTeamId));
        updateStudy(study, false);

        return study;
    }

    public Study updateStudy(Study study, boolean isAdminUpdate) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        // These cannot be set through the API and will be null here, so they are set on update
        Study originalStudy = studyDao.getStudy(study.getIdentifier());

        // And this cannot be set unless you're an administrator. Regardless of what the
        // developer set, set these back to the original study.
        if (!isAdminUpdate) {
            // prevent non-admins update a deactivated study
            if (!originalStudy.isActive()) {
                throw new EntityNotFoundException(Study.class, "Study '"+ study.getIdentifier() +"' not found.");
            }

            study.setHealthCodeExportEnabled(originalStudy.isHealthCodeExportEnabled());
            study.setEmailVerificationEnabled(originalStudy.isEmailVerificationEnabled());
        }

        // prevent anyone changing active to false -- it should be done by deactivateStudy() method
        if (originalStudy.isActive() && !study.isActive()) {
            throw new BadRequestException("Study cannot be deleted through an update.");
        }

        sanitizeHTML(study);

        study.setStormpathHref(originalStudy.getStormpathHref());
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
        
        if (!originalStudy.getSupportEmail().equals(study.getSupportEmail())) {
            emailVerificationService.verifyEmailAddress(study.getSupportEmail());
        }
        
        cacheProvider.setStudy(updatedStudy);
        
        return updatedStudy;
    }

    public void deleteStudy(String identifier, boolean physical) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        if (studyWhitelist.contains(identifier)) {
            throw new UnauthorizedException(identifier + " is protected by whitelist.");
        }

        // Verify the study exists before you do this
        // only admin can call this method, should contain deactivated ones.
        Study existing = getStudy(identifier, true);
        if (existing == null) {
            throw new EntityNotFoundException(Study.class, "Study '"+identifier+"' not found");
        }

        if (!physical) {
            // deactivate
            if (!existing.isActive()) {
                throw new BadRequestException("Study '"+identifier+"' is deactivated before.");
            }
            studyDao.deactivateStudy(existing.getIdentifier());
        } else {
            // actual delete
            studyDao.deleteStudy(existing);
            directoryDao.deleteDirectoryForStudy(existing);
            subpopService.deleteAllSubpopulations(existing.getStudyIdentifier());
        }

        cacheProvider.removeStudy(identifier);
    }
    
    /**
     * Has an aspect of the study changed that must be saved as well in the Stormpath directory? This 
     * includes the email templates but also all the fields that can be substituted into the email templates
     * such as names and emal addresses.
     * @param originalStudy
     * @param study
     * @return true if the password policy or email templates have changed
     */
    private boolean studyDirectoryHasChanged(Study originalStudy, Study study) {
        return (!study.getName().equals(originalStudy.getName()) ||
                !study.getSponsorName().equals(originalStudy.getSponsorName()) ||
                !study.getSupportEmail().equals(originalStudy.getSupportEmail()) ||
                !study.getTechnicalEmail().equals(originalStudy.getTechnicalEmail()) ||
                !study.getPasswordPolicy().equals(originalStudy.getPasswordPolicy()) || 
                !study.getVerifyEmailTemplate().equals(originalStudy.getVerifyEmailTemplate()) || 
                !study.getResetPasswordTemplate().equals(originalStudy.getResetPasswordTemplate()) || 
                study.isEmailVerificationEnabled() != originalStudy.isEmailVerificationEnabled());
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
                body = Jsoup.clean(body, BridgeConstants.CKEDITOR_WHITELIST);
            }
        }
        return new EmailTemplate(subject, body, template.getMimeType());
    }
}
