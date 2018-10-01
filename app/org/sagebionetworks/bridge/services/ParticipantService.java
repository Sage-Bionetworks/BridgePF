package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.AccountSummarySearchValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class ParticipantService {
    private static Logger LOG = LoggerFactory.getLogger(ParticipantService.class);

    private AccountDao accountDao;

    private SubpopulationService subpopService;

    private ConsentService consentService;

    private ExternalIdService externalIdService;

    private CacheProvider cacheProvider;

    private ScheduledActivityDao activityDao;

    private UploadService uploadService;

    private NotificationsService notificationsService;

    private ScheduledActivityService scheduledActivityService;

    private ActivityEventService activityEventService;

    private AccountWorkflowService accountWorkflowService;

    @Autowired
    public final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    @Autowired
    final void setUserConsent(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setScheduledActivityDao(ScheduledActivityDao activityDao) {
        this.activityDao = activityDao;
    }

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Autowired
    final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }

    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    /**
     * This is a researcher API to backfill SMS notification registrations for a user. We generally prefer the app
     * register notifications, but sometimes the work can't be done on time, so we want study developers to have the
     * option of backfilling these.
     */
    public void createSmsRegistration(Study study, String userId) {
        checkNotNull(study);
        checkNotNull(userId);

        // Account must have a verified phone number.
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), userId));
        if (account.getPhoneVerified() != Boolean.TRUE) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user has no verified phone number");
        }

        // We need the account's request info to build the criteria context.
        RequestInfo requestInfo = cacheProvider.getRequestInfo(userId);
        if (requestInfo == null) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user has no request info");
        }
        CriteriaContext criteriaContext = new CriteriaContext.Builder()
                .withStudyIdentifier(study.getStudyIdentifier())
                .withUserId(userId)
                .withHealthCode(account.getHealthCode())
                .withClientInfo(requestInfo.getClientInfo())
                .withLanguages(requestInfo.getLanguages())
                .withUserDataGroups(requestInfo.getUserDataGroups())
                .build();

        // Participant must be consented.
        StudyParticipant participant = getParticipant(study, account, true);
        if (participant.isConsented() != Boolean.TRUE) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user is not consented");
        }

        // Create registration.
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(account.getHealthCode());
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint(account.getPhone().getNumber());

        // Create registration.
        notificationsService.createRegistration(study.getStudyIdentifier(), criteriaContext, registration);
    }

    public StudyParticipant getParticipant(Study study, AccountId accountId, boolean includeHistory) {
        Account account = accountDao.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return getParticipant(study, account, includeHistory);
    }

    public StudyParticipant getParticipant(Study study, String id, boolean includeHistory) {
        return getParticipant(study, AccountId.forId(study.getIdentifier(),  id), includeHistory);
    }
    
    public StudyParticipant getParticipant(Study study, Account account, boolean includeHistory) {
        if (account == null) {
            // This should never happen. However, it occasionally does happen, generally only during integration tests.
            // If a call is taking a long time for whatever reason, the call will timeout and the tests will delete the
            // account. If this happens in the middle of a call (such as give consent or update self participant),
            // we'll suddenly have no account here.
            //
            // We'll still want to log an error for this so we'll be aware when it happens. At the very least, we'll
            // have this comment and a marginally useful error message instead of a mysterious null pointer exception.
            //
            // See https://sagebionetworks.jira.com/browse/BRIDGE-1463 for more info.
            LOG.error("getParticipant() called with no account. Was the account deleted in the middle of the call?");
            throw new EntityNotFoundException(Account.class);
        }

        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        builder.withSharingScope(account.getSharingScope());
        builder.withNotifyByEmail(account.getNotifyByEmail());
        builder.withExternalId(account.getExternalId());
        builder.withDataGroups(account.getDataGroups());
        builder.withLanguages(account.getLanguages());
        builder.withTimeZone(account.getTimeZone());
        builder.withFirstName(account.getFirstName());
        builder.withLastName(account.getLastName());
        builder.withEmail(account.getEmail());
        builder.withPhone(account.getPhone());
        builder.withEmailVerified(account.getEmailVerified());
        builder.withPhoneVerified(account.getPhoneVerified());
        builder.withStatus(account.getStatus());
        builder.withCreatedOn(account.getCreatedOn());
        builder.withRoles(account.getRoles());
        builder.withId(account.getId());
        builder.withHealthCode(account.getHealthCode());
        builder.withClientData(account.getClientData());

        Map<String, String> attributes = Maps.newHashMap();
        for (String attribute : study.getUserProfileAttributes()) {
            String value = account.getAttribute(attribute);
            attributes.put(attribute, value);
        }
        builder.withAttributes(attributes);

        if (includeHistory) {
            Map<String,List<UserConsentHistory>> consentHistories = Maps.newHashMap();
            List<Subpopulation> subpopulations = subpopService.getSubpopulations(study.getStudyIdentifier(), false);
            for (Subpopulation subpop : subpopulations) {
                // always returns a list, even if empty
                List<UserConsentHistory> history = getUserConsentHistory(account, subpop.getGuid());
                consentHistories.put(subpop.getGuidString(), history);
            }
            builder.withConsentHistories(consentHistories);

            // To calculate consent status, we need construct a CriteriaContext from RequestInfo.
            RequestInfo requestInfo = cacheProvider.getRequestInfo(account.getId());
            if (requestInfo != null) {
                CriteriaContext criteriaContext = new CriteriaContext.Builder()
                        .withStudyIdentifier(study.getStudyIdentifier())
                        .withUserId(account.getId())
                        .withHealthCode(account.getHealthCode())
                        .withClientInfo(requestInfo.getClientInfo())
                        .withLanguages(requestInfo.getLanguages())
                        .withUserDataGroups(requestInfo.getUserDataGroups())
                        .build();
                Map<SubpopulationGuid, ConsentStatus> consentStatusMap = consentService.getConsentStatuses(
                        criteriaContext, account);
                boolean isConsented = ConsentStatus.isUserConsented(consentStatusMap);
                builder.withConsented(isConsented);
            }
        }
        return builder.build();
    }

    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        checkNotNull(study);
        
        Validate.entityThrowingException(new AccountSummarySearchValidator(study.getDataGroups()), search);
        
        return accountDao.getPagedAccountSummaries(study, search);
    }

    public void signUserOut(Study study, String email, boolean deleteReauthToken) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));

        Account account = getAccountThrowingException(study, email);
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), account.getId());

        if (deleteReauthToken) {
            accountDao.deleteReauthToken(accountId);
        }
        
        cacheProvider.removeSessionByUserId(account.getId());
    }

    /**
     * Create a study participant. A password must be provided, even if it is added on behalf of a user before
     * triggering a reset password request.
     */
    public IdentifierHolder createParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant,
            boolean shouldSendVerification) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkNotNull(participant);
        
        if (study.getAccountLimit() > 0) {
            throwExceptionIfLimitMetOrExceeded(study);
        }
        Validate.entityThrowingException(new StudyParticipantValidator(externalIdService, study, null), participant);
        
        Account account = accountDao.constructAccount(study, participant.getEmail(), participant.getPhone(),
                participant.getExternalId(), participant.getPassword());

        updateAccountAndRoles(study, callerRoles, account, participant);
        
        account.setStatus(AccountStatus.UNVERIFIED);

        // enabled unless we need any kind of verification
        boolean sendEmailVerification = shouldSendVerification && study.isEmailVerificationEnabled();
        if (participant.getEmail() != null && !sendEmailVerification) {
            // not verifying, so consider it verified
            account.setEmailVerified(true); 
            account.setStatus(AccountStatus.ENABLED);
        }
        if (participant.getPhone() != null && !shouldSendVerification) {
            // not verifying, so consider it verified
            account.setPhoneVerified(true); 
            account.setStatus(AccountStatus.ENABLED);
        }
        // If external ID only was provided, then the account will need to be enabled through use of the 
        // the AuthenticationService.generatePassword() pathway.
        if (shouldEnableCompleteExternalIdAccount(participant)) {
            account.setStatus(AccountStatus.ENABLED);
        }
        String accountId = accountDao.createAccount(study, account);
        externalIdService.assignExternalId(study, participant.getExternalId(), account.getHealthCode());    
        
        // send verify email
        if (sendEmailVerification && !study.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(study, accountId, account.getEmail());
        }
        // send verify phone number
        if (shouldSendVerification && !study.isAutoVerificationPhoneSuppressed()) {
            accountWorkflowService.sendPhoneVerificationToken(study, accountId, account.getPhone());
        }
        return new IdentifierHolder(accountId);
    }
    
    private boolean shouldEnableCompleteExternalIdAccount(StudyParticipant participant) {
        return participant.getEmail() == null && participant.getPhone() == null && 
            participant.getExternalId() != null && participant.getPassword() != null;
    }

    public StudyParticipant updateParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkNotNull(participant);
        
        Account account = getAccountThrowingException(study, participant.getId());
        
        Validate.entityThrowingException(new StudyParticipantValidator(externalIdService, study, account), participant);
        
        updateAccountAndRoles(study, callerRoles, account, participant);
        
        boolean assigningExternalId = !Objects.equals(participant.getExternalId(), account.getExternalId());
        boolean assigningEmail = !Objects.equals(participant.getEmail(), account.getEmail());
        boolean assigningPhone = !Objects.equals(participant.getPhone(), account.getPhone());
        if (assigningExternalId) {
            if (account.getExternalId() != null) {
                externalIdService.unassignExternalId(study, account.getExternalId(), account.getHealthCode());
            }
            account.setExternalId(participant.getExternalId());
        }
        if (assigningEmail) {
            account.setEmail(participant.getEmail());
            account.setEmailVerified(false);
        }
        if (assigningPhone) {
            account.setPhone(participant.getPhone());
            account.setPhoneVerified(false);
        }
        
        // Allow admin and worker accounts to toggle status; in particular, to disable/enable accounts. Note 
        // however that admins can bypass phone/email verification as a result.
        if (participant.getStatus() != null) {
            if (callerRoles.contains(Roles.ADMIN) || callerRoles.contains(Roles.WORKER)) {
                account.setStatus(participant.getStatus());
            }
        }
        accountDao.updateAccount(account, true);
        
        if (assigningExternalId) {
            externalIdService.assignExternalId(study, account.getExternalId(), account.getHealthCode());    
        }
        // Don't send verification if you're removing an email address
        if (assigningEmail && account.getEmail() != null && !study.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(study, account.getId(), account.getEmail());
        }
        // Don't send verification if you're removing a phone number
        if (assigningPhone && account.getPhone() != null && !study.isAutoVerificationPhoneSuppressed()) {
            accountWorkflowService.sendPhoneVerificationToken(study, account.getId(), account.getPhone());
        }
        return getParticipant(study, account, true);
    }
    
    private void throwExceptionIfLimitMetOrExceeded(Study study) {
        // It's sufficient to get minimum number of records, we're looking only at the total of all accounts
        PagedResourceList<AccountSummary> summaries = getPagedAccountSummaries(study, AccountSummarySearch.EMPTY_SEARCH);
        if (summaries.getTotal() >= study.getAccountLimit()) {
            throw new LimitExceededException(String.format(BridgeConstants.MAX_USERS_ERROR, study.getAccountLimit()));
        }
    }

    private void updateAccountAndRoles(Study study, Set<Roles> callerRoles, Account account,
            StudyParticipant participant) {
        account.setFirstName(participant.getFirstName());
        account.setLastName(participant.getLastName());
        account.setClientData(participant.getClientData());
        account.setSharingScope(participant.getSharingScope());
        account.setNotifyByEmail(participant.isNotifyByEmail());
        account.setDataGroups(participant.getDataGroups());
        account.setLanguages(participant.getLanguages());
        account.setMigrationVersion(AccountDao.MIGRATION_VERSION);
        // Do not copy timezone. Cannot be updated once set. Email, phone, and externalId are all handled separately.
        
        for (String attribute : study.getUserProfileAttributes()) {
            String value = participant.getAttributes().get(attribute);
            account.setAttribute(attribute, value);
        }
        if (callerIsAdmin(callerRoles)) {
            updateRoles(callerRoles, participant, account);
        }
    }

    public void requestResetPassword(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        // Don't throw an exception here, you'd be exposing that an email/phone number is in the system.
        AccountId accountId = AccountId.forId(study.getIdentifier(), userId);

        accountWorkflowService.requestResetPassword(study, true, accountId);
    }

    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(Study study, String userId,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize) {
        checkNotNull(study);
        checkArgument(isNotBlank(activityGuid));
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(study, userId);

        return scheduledActivityService.getActivityHistory(account.getHealthCode(), activityGuid, scheduledOnStart,
                scheduledOnEnd, offsetKey, pageSize);
    }
    
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(Study study, String userId,
            ActivityType activityType, String referentGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd,
            String offsetKey, int pageSize) {

        Account account = getAccountThrowingException(study, userId);

        return scheduledActivityService.getActivityHistory(account.getHealthCode(), activityType, referentGuid,
                scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
    }

    public void deleteActivities(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(study, userId);

        activityDao.deleteActivitiesForUser(account.getHealthCode());
    }

    public void resendVerification(Study study, ChannelType type, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(study, userId, false);
        if (type == ChannelType.EMAIL) { 
            if (participant.getEmail() != null) {
                AccountId accountId = AccountId.forEmail(study.getIdentifier(), participant.getEmail());
                accountWorkflowService.resendVerificationToken(type, accountId);
            }
        } else if (type == ChannelType.PHONE) {
            if (participant.getPhone() != null) {
                AccountId accountId = AccountId.forPhone(study.getIdentifier(), participant.getPhone());
                accountWorkflowService.resendVerificationToken(type, accountId);
            }
        } else {
            throw new UnsupportedOperationException("Channel type not implemented");
        }
    }

    public void withdrawFromStudy(Study study, String userId, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        StudyParticipant participant = getParticipant(study, userId, false);

        consentService.withdrawFromStudy(study, participant, withdrawal, withdrewOn);
    }

    public void withdrawConsent(Study study, String userId,
            SubpopulationGuid subpopGuid, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(subpopGuid);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        StudyParticipant participant = getParticipant(study, userId, false);
        CriteriaContext context = getCriteriaContextForParticipant(study, participant);

        consentService.withdrawConsent(study, subpopGuid, participant, context, withdrawal, withdrewOn);
    }
    
    public void resendConsentAgreement(Study study, SubpopulationGuid subpopGuid, String userId) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(study, userId, false);
        consentService.resendConsentAgreement(study, subpopGuid, participant);
    }

    /**
     * Get a history of all consent records for a given subpopulation, whether user is withdrawn or not.
     */
    public List<UserConsentHistory> getUserConsentHistory(Account account, SubpopulationGuid subpopGuid) {
        return account.getConsentSignatureHistory(subpopGuid).stream().map(signature -> {
            Subpopulation subpop = subpopService.getSubpopulation(account.getStudyIdentifier(), subpopGuid);
            boolean hasSignedActiveConsent = (signature.getConsentCreatedOn() == subpop.getPublishedConsentCreatedOn());

            return new UserConsentHistory.Builder()
                .withName(signature.getName())
                .withSubpopulationGuid(subpopGuid)
                .withBirthdate(signature.getBirthdate())
                .withImageData(signature.getImageData())
                .withImageMimeType(signature.getImageMimeType())
                .withSignedOn(signature.getSignedOn())
                .withHealthCode(account.getHealthCode())
                .withWithdrewOn(signature.getWithdrewOn())
                .withConsentCreatedOn(signature.getConsentCreatedOn())
                .withHasSignedActiveConsent(hasSignedActiveConsent).build();
        }).collect(BridgeCollectors.toImmutableList());
    }

    public ForwardCursorPagedResourceList<UploadView> getUploads(Study study, String userId, DateTime startTime,
            DateTime endTime, Integer pageSize, String offsetKey) {
        checkNotNull(study);
        checkNotNull(userId);
        
        Account account = getAccountThrowingException(study, userId);

        return uploadService.getUploads(account.getHealthCode(), startTime, endTime, pageSize, offsetKey);
    }

    public List<NotificationRegistration> listRegistrations(Study study, String userId) {
        checkNotNull(study);
        checkNotNull(userId);

        Account account = getAccountThrowingException(study, userId);

        return notificationsService.listRegistrations(account.getHealthCode());
    }

    public Set<String> sendNotification(Study study, String userId, NotificationMessage message) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(message);

        Account account = getAccountThrowingException(study, userId);

        return notificationsService.sendNotificationToUser(study.getStudyIdentifier(), account.getHealthCode(), message);
    }

    /**
     * Send an SMS message to this user if they have a verified phone number. This message will be 
     * sent with AWS' non-critical, "Promotional" level of delivery that optimizes for cost.
     */
    public void sendSmsMessage(Study study, String userId, SmsTemplate template) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(template);
        
        if (StringUtils.isBlank(template.getMessage())) {
            throw new BadRequestException("Message is required");
        }
        Account account = getAccountThrowingException(study, userId);
        if (account.getPhone() == null || account.getPhoneVerified() != Boolean.TRUE) {
            throw new BadRequestException("Account does not have a verified phone number");
        }
        Map<String,String> variables = BridgeUtils.studyTemplateVariables(study);
        
        SmsMessageProvider.Builder builder = new SmsMessageProvider.Builder()
                .withPhone(account.getPhone())
                .withSmsTemplate(template)
                .withPromotionType()
                .withStudy(study);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            builder.withToken(entry.getKey(), entry.getValue());
        }
        notificationsService.sendSmsMessage(builder.build());
    }
    
    public List<ActivityEvent> getActivityEvents(Study study, String userId) {
        Account account = getAccountThrowingException(study, userId);

        return activityEventService.getActivityEventList(account.getHealthCode());
    }
    
    private CriteriaContext getCriteriaContextForParticipant(Study study, StudyParticipant participant) {
        RequestInfo info = cacheProvider.getRequestInfo(participant.getId());
        ClientInfo clientInfo = (info == null) ? null : info.getClientInfo();
        
        return new CriteriaContext.Builder()
            .withStudyIdentifier(study.getStudyIdentifier())
            .withHealthCode(participant.getHealthCode())
            .withUserId(participant.getId())
            .withClientInfo(clientInfo)
            .withUserDataGroups(participant.getDataGroups())
            .withLanguages(participant.getLanguages()).build();
    }

    private boolean callerIsAdmin(Set<Roles> callerRoles) {
        return !Collections.disjoint(callerRoles, ADMINISTRATIVE_ROLES);
    }

    private boolean callerCanEditRole(Set<Roles> callerRoles, Roles targetRole) {
        return !Collections.disjoint(callerRoles, CAN_BE_EDITED_BY.get(targetRole));
    }

    /**
     * For each role added, the caller must have the right to add the role. Then for every role currently assigned, we
     * check and if the caller doesn't have the right to remove that role, we'll add it back. Then we save those
     * results.
     */
    private void updateRoles(Set<Roles> callerRoles, StudyParticipant participant, Account account) {
        Set<Roles> newRoleSet = Sets.newHashSet();
        // Caller can only add roles they have the rights to edit
        for (Roles role : participant.getRoles()) {
            if (callerCanEditRole(callerRoles, role)) {
                newRoleSet.add(role);
            }
        }
        // Callers also can't remove roles they don't have the rights to edit
        for (Roles role : account.getRoles()) {
            if (!callerCanEditRole(callerRoles, role)) {
                newRoleSet.add(role);
            }
        }
        account.setRoles(newRoleSet);
    }

    private Account getAccountThrowingException(Study study, String id) {
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), id));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }

}
