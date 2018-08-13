package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TWO_MONTHS_BEFORE_ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TWO_WEEKS_BEFORE_ENROLLMENT;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
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
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
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
import org.sagebionetworks.bridge.validators.IdentifierUpdateValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class ParticipantService {
    private static Logger LOG = LoggerFactory.getLogger(ParticipantService.class);
    private static final String TWO_WEEKS = TWO_WEEKS_BEFORE_ENROLLMENT.name().toLowerCase();
    private static final String TWO_MONTHS = TWO_MONTHS_BEFORE_ENROLLMENT.name().toLowerCase();

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
            List<Subpopulation> subpopulations = subpopService.getSubpopulations(study.getStudyIdentifier());
            for (Subpopulation subpop : subpopulations) {
                // always returns a list, even if empty
                List<UserConsentHistory> history = getUserConsentHistory(account, subpop.getGuid());
                consentHistories.put(subpop.getGuidString(), history);
            }
            builder.withConsentHistories(consentHistories);
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
        Validate.entityThrowingException(new StudyParticipantValidator(externalIdService, study, true), participant);
        
        Account account = accountDao.constructAccount(study, participant.getEmail(), participant.getPhone(),
                participant.getExternalId(), participant.getPassword());

        updateAccountAndRoles(study, callerRoles, account, participant);

        account.setStatus(AccountStatus.ENABLED);

        // enabled unless we need any kind of verification
        boolean sendEmailVerification = shouldSendVerification && study.isEmailVerificationEnabled();
        if (participant.getEmail() != null) {
            if (sendEmailVerification) {
                account.setStatus(AccountStatus.UNVERIFIED);
            } else {
                account.setEmailVerified(true); // not verifying, so consider it verified if it exists
            }
        }
        if (participant.getPhone() != null) {
            if (shouldSendVerification) {
                account.setStatus(AccountStatus.UNVERIFIED);
            } else {
                account.setPhoneVerified(true); // not verifying, so consider it verified if it exists
            }
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

    public void updateParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkNotNull(participant);
        
        Validate.entityThrowingException(new StudyParticipantValidator(externalIdService, study, false), participant);
        
        Account account = getAccountThrowingException(study, participant.getId());

        // Prevent optimistic locking exception until operations are combined into one operation. 
        account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), account.getId()));
        // Allow external ID to be added on an update if it doesn't exist.
        
        boolean assigningExternalId = (account.getExternalId() == null && participant.getExternalId() != null);
        if (assigningExternalId) {
            account.setExternalId(participant.getExternalId());    
        }
        updateAccountAndRoles(study, callerRoles, account, participant);
        
        // Only Admin and Worker accounts controlled by us should be able to bypass email verification. This is
        // primarily used for integration tests, but is sometimes used to bootstrap external developers and
        // researchers.
        if (participant.getStatus() != null) {
            if (callerRoles.contains(Roles.ADMIN) || callerRoles.contains(Roles.WORKER)) {
                account.setStatus(participant.getStatus());
            }
        }
        accountDao.updateAccount(account, false);
        
        if (assigningExternalId) {
            externalIdService.assignExternalId(study, account.getExternalId(), account.getHealthCode());    
        }
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
        // Do not copy timezone or external ID. Neither can be updated once set.
        
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

    public void withdrawAllConsents(Study study, String userId, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        StudyParticipant participant = getParticipant(study, userId, false);
        CriteriaContext context = getCriteriaContextForParticipant(study, participant);

        consentService.withdrawAllConsents(study, participant, context, withdrawal, withdrewOn);
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
     * 
     * @param account
     * @param subpopGuid
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

    public void sendNotification(Study study, String userId, NotificationMessage message) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(message);

        Account account = getAccountThrowingException(study, userId);

        notificationsService.sendNotificationToUser(study.getStudyIdentifier(), account.getHealthCode(), message);
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
    
    public void deleteAllActivityEvents(Study study, String userId) {
        Account account = getAccountThrowingException(study, userId);
        
        activityEventService.deleteActivityEvents(account.getHealthCode());
    }

    public void deleteActivityEvent(Study study, String userId, String eventId) {
        Account account = getAccountThrowingException(study, userId);
        
        if (StringUtils.isBlank(eventId)) {
            throw new BadRequestException("Event ID required");
        }
        activityEventService.deleteActivityEvent(account.getHealthCode(), eventId);
    }

    public void publishActivityEvent(Study study, String userId, CustomActivityEventRequest activityEvent) {
        Account account = getAccountThrowingException(study, userId);
        
        if (activityEvent == null || StringUtils.isBlank(activityEvent.getEventKey())
                || activityEvent.getTimestamp() == null) {
            throw new BadRequestException("Invalid activity event (missing fields)");
        }
        
        // Dependent (calculated) keys are immutable. Do not change them.
        String eventId = activityEvent.getEventKey();
        if (eventId.equals(TWO_WEEKS) || eventId.equals(TWO_MONTHS)) {
            throw new BadRequestException("Activity event key '"+eventId+"' is immutable");    
        }
        for (String customKey : study.getAutomaticCustomEvents().keySet()) {
             if (eventId.equals(customKey) || activityEvent.getEventKey().equals("custom:" + customKey)) {
                throw new BadRequestException("Activity event key '"+eventId+"' is immutable");
            }
        }
        // No type-safety here. If the user submits junk, it'll be ignored
        DynamoActivityEvent event = new DynamoActivityEvent();
        event.setHealthCode(account.getHealthCode());
        event.setEventId(activityEvent.getEventKey());
        event.setTimestamp(activityEvent.getTimestamp().getMillis());
        event.setAnswerValue(activityEvent.getAnswerValue());
        
        activityEventService.publishActivityEvent(event, false);
    }
    
    public StudyParticipant updateIdentifiers(Study study, CriteriaContext context, IdentifierUpdate update) {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(update);
        
        // Validate
        Validate.entityThrowingException(new IdentifierUpdateValidator(study, externalIdService), update);
        
        // Sign in
        Account account = null;
        // These throw exceptions for not found, disabled, and not yet verified.
        if (update.getSignIn().getReauthToken() != null) {
            account = accountDao.reauthenticate(study, update.getSignIn());
        } else {
            account = accountDao.authenticate(study, update.getSignIn());
        }
        
        // Verify the account matches the current caller
        if (!account.getId().equals(context.getUserId())) {
            throw new EntityNotFoundException(Account.class);
        }
        
        // reload account, or you will get an optimistic lock exception
        account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), account.getId()));
        
        // Update if account has an empty field and there's an update
        boolean sendEmailVerification = false;
        boolean assignExternalId = false;
        boolean accountUpdated = false;
        if (update.getPhoneUpdate() != null && account.getPhone() == null) {
            account.setPhone(update.getPhoneUpdate());
            account.setPhoneVerified(false);
            accountUpdated = true;
        }
        if (update.getEmailUpdate() != null && account.getEmail() == null) {
            account.setEmail(update.getEmailUpdate());
            account.setEmailVerified( !study.isEmailVerificationEnabled() );
            sendEmailVerification = true;
            accountUpdated = true;
        }
        if (update.getExternalIdUpdate() != null && account.getExternalId() == null) {
            account.setExternalId(update.getExternalIdUpdate());
            accountUpdated = true;
            assignExternalId = true;
        }
        // save. if this throws a constraint exception, further services are not called
        if (accountUpdated) {
            accountDao.updateAccount(account, true);   
        }
        if (sendEmailVerification && 
            study.isEmailVerificationEnabled() && 
            !study.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(study, account.getId(), account.getEmail());
        }
        if (assignExternalId) {
            externalIdService.assignExternalId(study, account.getExternalId(), account.getHealthCode());
        }
        
        // return updated StudyParticipant to update and return session
        return getParticipant(study, account.getId(), false);
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
