package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.client.exceptions.SynapseServerException;
import org.sagebionetworks.repo.model.MembershipInvtnSubmission;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.auth.NewUser;
import org.sagebionetworks.repo.model.util.ModelConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component("studyService")
public class StudyService {
    private static Logger LOG = LoggerFactory.getLogger(StudyService.class);

    static final String EXPORTER_SYNAPSE_USER_ID = BridgeConfigFactory.getConfig().getExporterSynapseId(); // copy-paste from website
    static final String SYNAPSE_REGISTER_END_POINT = "https://www.synapse.org/#!NewAccount:";
    private final Set<String> studyWhitelist = Collections.unmodifiableSet(new HashSet<>(
            BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private CompoundActivityDefinitionService compoundActivityDefinitionService;
    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    private SubpopulationService subpopService;
    private NotificationTopicService topicService;
    private EmailVerificationService emailVerificationService;
    private SynapseClient synapseClient;
    private ParticipantService participantService;
    private SchedulePlanService schedulePlanService;

    private String defaultEmailVerificationTemplate;
    private String defaultEmailVerificationTemplateSubject;
    private String defaultResetPasswordTemplate;
    private String defaultResetPasswordTemplateSubject;
    private String defaultEmailSignInTemplate;
    private String defaultEmailSignInTemplateSubject;
    
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
    @Value("classpath:study-defaults/email-sign-in.txt")
    final void setDefaultEmailSignInTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:study-defaults/email-sign-in-subject.txt")
    final void setDefaultEmailSignInTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    /** Compound activity definition service, used to clean up deleted studies. This is set by Spring. */
    @Autowired
    final void setCompoundActivityDefinitionService(
            CompoundActivityDefinitionService compoundActivityDefinitionService) {
        this.compoundActivityDefinitionService = compoundActivityDefinitionService;
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
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }
    @Autowired
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    @Autowired
    @Qualifier("bridgePFSynapseClient")
    public final void setSynapseClient(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }

    public Study getStudy(String identifier, boolean includeDeleted) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }
        if (study != null) {
            // If it it exists and has been deactivated, and this call is not supposed to retrieve deactivated
            // studies, treat it as if it doesn't exist.
            if (!study.isActive() && !includeDeleted) {
                throw new EntityNotFoundException(Study.class, "Study not found.");
            }
            // Because this template does not currently exist in studies, add the default if it is null.
            if (study.getEmailSignInTemplate() == null) {
                EmailTemplate template = new EmailTemplate(defaultEmailSignInTemplateSubject,
                        defaultEmailSignInTemplate, MimeType.HTML);
                study.setEmailSignInTemplate(template);
            }
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

    public Study createStudyAndUsers(StudyAndUsers studyAndUsers) throws SynapseException {
        checkNotNull(studyAndUsers, Validate.CANNOT_BE_NULL, "study and users");

        List<String> adminIds = studyAndUsers.getAdminIds();
        if (adminIds == null || adminIds.isEmpty()) {
            throw new BadRequestException("Admin IDs are required.");
        }
        // validate if each admin id is a valid synapse id in synapse
        for (String adminId : adminIds) {
            try {
                synapseClient.getUserProfile(adminId);
            } catch (SynapseNotFoundException e) {
                throw new BadRequestException("Admin ID is invalid.");
            }
        }

        List<StudyParticipant> users = studyAndUsers.getUsers();
        if (users == null || users.isEmpty()) {
            throw new BadRequestException("User list is required.");
        }
        if (studyAndUsers.getStudy() == null) {
            throw new BadRequestException("Study cannot be null.");
        }
        Study study = studyAndUsers.getStudy();
        // prevent NPE in participant validation
        if (study.getPasswordPolicy() == null) {
            study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }

        // validate participants at first
        for (StudyParticipant user : users) {
            Validate.entityThrowingException(new StudyParticipantValidator(study, true), user);
        }

        // validate roles for each user
        for (StudyParticipant user: users) {
            if (!Collections.disjoint(user.getRoles(), ImmutableSet.of(Roles.ADMIN, Roles.TEST_USERS, Roles.WORKER))) {
                throw new BadRequestException("User can only have roles developer and/or researcher.");
            }
            if (user.getRoles().isEmpty()) {
                throw new BadRequestException("User should have at least one role.");
            }
        }

        // then create and validate study
        study = createStudy(study);

        // then create users for that study
        // send verification email from both Bridge and Synapse as well
        for (StudyParticipant user: users) {
            IdentifierHolder identifierHolder = participantService.createParticipant(study, user.getRoles(), user,true);

            NewUser synapseUser = new NewUser();
            synapseUser.setEmail(user.getEmail());
            try {
                synapseClient.newAccountEmailValidation(synapseUser, SYNAPSE_REGISTER_END_POINT);
            } catch (SynapseServerException e) {
                if (!"The email address provided is already used.".equals(e.getMessage())) {
                    throw e;
                } else {
                    LOG.info("Email: " + user.getEmail() + " already exists in Synapse", e);
                }
            }
            // send resetting password email as well
            participantService.requestResetPassword(study, identifierHolder.getIdentifier());
        }

        // finally create synapse project and team
        createSynapseProjectTeam(studyAndUsers.getAdminIds(), study);

        return study;
    }

    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        if (study.getVersion() != null){
            throw new EntityAlreadyExistsException(Study.class, "Study has a version value; it may already exist",
                new ImmutableMap.Builder<String,Object>().put("identifier", study.getIdentifier()).build());
        }

        study.setActive(true);
        study.setStrictUploadValidationEnabled(true);
        study.setEmailVerificationEnabled(true);
        study.setEmailSignInEnabled(false);
        study.getDataGroups().add(BridgeConstants.TEST_USER_GROUP);
        setDefaultsIfAbsent(study);
        sanitizeHTML(study);
        Validate.entityThrowingException(validator, study);

        if (studyDao.doesIdentifierExist(study.getIdentifier())) {
            throw new EntityAlreadyExistsException(Study.class, "identifier", study.getIdentifier());
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

    public Study createSynapseProjectTeam(List<String> synapseUserIds, Study study) throws SynapseException {
        if (synapseUserIds == null || synapseUserIds.isEmpty()) {
            throw new BadRequestException("Synapse User IDs are required.");
        }
        // first check if study already has project and team ids
        if (study.getSynapseDataAccessTeamId() != null){
            throw new EntityAlreadyExistsException(Study.class, "Study already has a team ID.",
                new ImmutableMap.Builder<String,Object>().put("identifier", study.getIdentifier())
                    .put("synapseDataAccessTeamId", study.getSynapseDataAccessTeamId()).build());
        }
        if (study.getSynapseProjectId() != null){
            throw new EntityAlreadyExistsException(Study.class, "Study already has a project ID.",
                new ImmutableMap.Builder<String,Object>().put("identifier", study.getIdentifier())
                .put("synapseProjectId", study.getSynapseProjectId()).build());
        }

        // then check if the user id exists
        for (String userId : synapseUserIds) {
            try {
                synapseClient.getUserProfile(userId);
            } catch (SynapseNotFoundException e) {
                throw new BadRequestException("Synapse User Id: " + userId + " is invalid.");
            }
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
        // add users as admins
        for (String synapseUserId : synapseUserIds) {
            ResourceAccess toSetUser = new ResourceAccess();
            toSetUser.setPrincipalId(Long.parseLong(synapseUserId)); // passed by user as parameter
            toSetUser.setAccessType(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
            acl.getResourceAccess().add(toSetUser);
        }
        // add team in project as well
        ResourceAccess toSetTeam = new ResourceAccess();
        toSetTeam.setPrincipalId(Long.parseLong(newTeam.getId())); // passed by user as parameter
        toSetTeam.setAccessType(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
        acl.getResourceAccess().add(toSetTeam);

        synapseClient.updateACL(acl);

        for (String synapseUserId : synapseUserIds) {
            // send invitation to target user for joining new team and grant admin permission to that user
            MembershipInvtnSubmission teamMemberInvitation = new MembershipInvtnSubmission();
            teamMemberInvitation.setInviteeId(synapseUserId);
            teamMemberInvitation.setTeamId(newTeam.getId());
            synapseClient.createMembershipInvitation(teamMemberInvitation, null, null);
            synapseClient.setTeamMemberPermissions(newTeam.getId(), synapseUserId, true);
        }

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
        
        checkViolationConstraints(study);
        
        // And this cannot be set unless you're an administrator. Regardless of what the
        // developer set, set these back to the original study.
        if (!isAdminUpdate) {
            // prevent non-admins update a deactivated study
            if (!originalStudy.isActive()) {
                throw new EntityNotFoundException(Study.class, "Study '"+ study.getIdentifier() +"' not found.");
            }
            study.setHealthCodeExportEnabled(originalStudy.isHealthCodeExportEnabled());
            study.setEmailVerificationEnabled(originalStudy.isEmailVerificationEnabled());
            study.setExternalIdValidationEnabled(originalStudy.isExternalIdValidationEnabled());
            study.setExternalIdRequiredOnSignup(originalStudy.isExternalIdRequiredOnSignup());
            study.setEmailSignInEnabled(originalStudy.isEmailSignInEnabled());
        }

        // prevent anyone changing active to false -- it should be done by deactivateStudy() method
        if (originalStudy.isActive() && !study.isActive()) {
            throw new BadRequestException("Study cannot be deleted through an update.");
        }
        
        // With the introduction of the session verification email, studies won't have all the templates
        // that are normally required. So set it if someone tries to update a study, to a default value.
        setDefaultsIfAbsent(study);
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
                throw new BadRequestException("Study '"+identifier+"' already deactivated.");
            }
            studyDao.deactivateStudy(existing.getIdentifier());
        } else {
            // actual delete
            studyDao.deleteStudy(existing);
            directoryDao.deleteDirectoryForStudy(existing);

            // delete study data
            compoundActivityDefinitionService.deleteAllCompoundActivityDefinitionsInStudy(
                    existing.getStudyIdentifier());
            subpopService.deleteAllSubpopulations(existing.getStudyIdentifier());
            topicService.deleteAllTopics(existing.getStudyIdentifier());
        }

        cacheProvider.removeStudy(identifier);
    }
    
    /**
     * The user cannot remove data groups already used by criteria, or task identifiers already used in schedules. If
     * these entities contain data groups or identifiers that are not in the updated version of the study, this is a
     * constraint violation.
     */
    private void checkViolationConstraints(Study study) {
        final Set<String> taskIds = study.getTaskIdentifiers();
        final Set<String> dataGroups = study.getDataGroups();
        
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, study);

        // If the study is *missing* a task identifier or a data group that is currently in use in a plan, 
        // that's a constraint error.
        for (SchedulePlan plan : plans) {
            for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
                for (Activity activity : schedule.getActivities()) {
                    if (activity.getTask() != null && !taskIds.contains(activity.getTask().getIdentifier())) {
                        throwConstraintViolation(plan, study);
                    }
                }
            }
            if (plan.getStrategy() instanceof CriteriaScheduleStrategy) {
                CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
                for (ScheduleCriteria scheduleCriteria : strategy.getScheduleCriteria()) {
                    if (!studyHasCriteriaDataGroups(dataGroups, scheduleCriteria.getCriteria())) {
                        throwConstraintViolation(plan, study);
                    }
                }
                
            }
        }

        // If the study is missing a dataGroup that is used by a subpopulation, that's a constraint error
        // This does not include logically deleted subpopulations, so these may be corrupted when restored.  
        List<Subpopulation> subpopulations = subpopService.getSubpopulations(study);
        for (Subpopulation subpop : subpopulations) {
            if (!subpop.isDeleted() && !studyHasCriteriaDataGroups(study.getDataGroups(), subpop.getCriteria())) {
                throwConstraintViolation(subpop, study);
            }
        }
    }
    
    private boolean studyHasCriteriaDataGroups(Set<String> studyDataGroups, Criteria criteria) {
        return studyDataGroups.containsAll(criteria.getAllOfGroups()) &&
                studyDataGroups.containsAll(criteria.getNoneOfGroups());
    }
    
    private void throwConstraintViolation(SchedulePlan match, Study study) {
        throw new ConstraintViolationException.Builder().withEntityKey("identifier", study.getIdentifier())
                .withEntityKey("type", "Study").withReferrerKey("guid", match.getGuid())
                .withReferrerKey("type", "SchedulePlan").build();
    }
    
    private void throwConstraintViolation(Subpopulation match, Study study) {
        throw new ConstraintViolationException.Builder().withEntityKey("identifier", study.getIdentifier())
                .withEntityKey("type", "Study").withReferrerKey("guid", match.getGuidString())
                .withReferrerKey("type", "Subpopulation").build();
    }
    
    /**
     * Has an aspect of the study changed that must be saved as well in the Stormpath directory? This 
     * includes the email templates for emails sent by Stormpath, but also all the fields that can be 
     * substituted into the email templates such as names and emal addresses.
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
     * When the password policy or templates are not included, they are set to some sensible defaults.  
     * values. 
     * @param study
     */
    private void setDefaultsIfAbsent(Study study) {
        if (study.getPasswordPolicy() == null) {
            study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }
        if (study.getVerifyEmailTemplate() == null) {
            EmailTemplate template = new EmailTemplate(defaultEmailVerificationTemplateSubject,
                    defaultEmailVerificationTemplate, MimeType.HTML);
            study.setVerifyEmailTemplate(template);
        }
        if (study.getResetPasswordTemplate() == null) {
            EmailTemplate template = new EmailTemplate(defaultResetPasswordTemplateSubject,
                    defaultResetPasswordTemplate, MimeType.HTML);
            study.setResetPasswordTemplate(template);
        }
        if (study.getEmailSignInTemplate() == null) {
            EmailTemplate template = new EmailTemplate(defaultEmailSignInTemplateSubject,
                    defaultEmailSignInTemplate, MimeType.HTML);
            study.setEmailSignInTemplate(template);
        }
    }

    /**
     * Email templates can contain HTML. Ensure the subject text has no markup and the markup in the body 
     * is safe for display in web-based email clients and a researcher UI. We clean this up before 
     * validation in case only unacceptable content was in the template. 
     * @param study
     */
    protected void sanitizeHTML(Study study) {
        EmailTemplate template = study.getVerifyEmailTemplate();
        study.setVerifyEmailTemplate(sanitizeEmailTemplate(template));
        
        template = study.getResetPasswordTemplate();
        study.setResetPasswordTemplate(sanitizeEmailTemplate(template));
        
        template = study.getEmailSignInTemplate();
        study.setEmailSignInTemplate(sanitizeEmailTemplate(template));
    }
    
    protected EmailTemplate sanitizeEmailTemplate(EmailTemplate template) {
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
