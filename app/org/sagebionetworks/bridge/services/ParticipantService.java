package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
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
import org.sagebionetworks.bridge.BridgeUtils.SubstudyAssociations;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
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
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
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
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
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
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantService.class);

    private AccountDao accountDao;

    private SmsService smsService;

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
    
    private SubstudyService substudyService;

    @Autowired
    public final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    /** SMS Service, used to send text messages to participants. */
    @Autowired
    public void setSmsService(SmsService smsService) {
        this.smsService = smsService;
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

    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    
    public void assignExternalId(AccountId accountId, ExternalIdentifier externalId) {
        Account account = getAccountThrowingException(accountId);
        ExternalIdentifier extIdObj = beginAssignExternalId(account, externalId.getIdentifier());
        externalIdService.commitAssignExternalId(extIdObj);
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
        Account account = getAccountThrowingException(AccountId.forId(study.getIdentifier(), userId));
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
        Set<String> substudyIds = account.getAccountSubstudies().stream()
                .map(AccountSubstudy::getSubstudyId).collect(BridgeCollectors.toImmutableSet());
        CriteriaContext criteriaContext = new CriteriaContext.Builder()
                .withStudyIdentifier(study.getStudyIdentifier())
                .withUserId(userId)
                .withHealthCode(account.getHealthCode())
                .withClientInfo(requestInfo.getClientInfo())
                .withLanguages(requestInfo.getLanguages())
                .withUserDataGroups(account.getDataGroups())
                .withUserSubstudyIds(substudyIds)
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

    public StudyParticipant getParticipant(Study study, String userId, boolean includeHistory) {
        // This parse method correctly deserializes formats such as externalId:XXXXXXXX.
        AccountId accountId = BridgeUtils.parseAccountId(study.getIdentifier(), userId);
        Account account = getAccountThrowingException(accountId);
        return getParticipant(study, account, includeHistory);
    }
    
    public StudyParticipant getSelfParticipant(Study study, CriteriaContext context, boolean includeHistory) {
        AccountId accountId = AccountId.forId(study.getIdentifier(),  context.getUserId());
        Account account = getAccountThrowingException(accountId); // already filters for substudy
        
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        SubstudyAssociations assoc = BridgeUtils.substudyAssociationsVisibleToCaller(account.getAccountSubstudies());
        addAccount(builder, assoc, account);
        addConsentStatus(builder, account, context);
        if (includeHistory) {
            addHistory(builder, account, context.getStudyIdentifier());
        }
        return builder.build();
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
        if (BridgeUtils.filterForSubstudy(account) == null) {
            throw new EntityNotFoundException(Account.class);
        }

        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        SubstudyAssociations assoc = BridgeUtils.substudyAssociationsVisibleToCaller(account.getAccountSubstudies());
        addAccount(builder, assoc, account);

        if (includeHistory) {
            addHistory(builder, account, study.getStudyIdentifier());
        }
        // Without requestInfo, we cannot reliably determine if the user is consented
        RequestInfo requestInfo = cacheProvider.getRequestInfo(account.getId());
        if (requestInfo != null) {
            CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(study.getStudyIdentifier())
                .withUserId(account.getId())
                .withHealthCode(account.getHealthCode())
                .withUserDataGroups(account.getDataGroups())
                .withUserSubstudyIds(assoc.getSubstudyIdsVisibleToCaller())
                .withClientInfo(requestInfo.getClientInfo())
                .withLanguages(requestInfo.getLanguages()).build();
            addConsentStatus(builder, account, context);
        }
        return builder.build();
    }
    
    private StudyParticipant.Builder addAccount(StudyParticipant.Builder builder, SubstudyAssociations assoc,
            Account account) {
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
        builder.withAttributes(account.getAttributes());
        builder.withSubstudyIds(assoc.getSubstudyIdsVisibleToCaller());
        builder.withExternalIds(assoc.getExternalIdsVisibleToCaller());
        return builder;
    }
    
    private StudyParticipant.Builder addHistory(StudyParticipant.Builder builder, Account account, StudyIdentifier studyId) {
        Map<String,List<UserConsentHistory>> consentHistories = Maps.newHashMap();
        // The history includes all subpopulations whether they match the user or not.
        List<Subpopulation> subpopulations = subpopService.getSubpopulations(studyId, false);
        for (Subpopulation subpop : subpopulations) {
            // always returns a list, even if empty
            List<UserConsentHistory> history = getUserConsentHistory(account, subpop.getGuid());
            consentHistories.put(subpop.getGuidString(), history);
        }
        builder.withConsentHistories(consentHistories);
        return builder;
    }
    
    private StudyParticipant.Builder addConsentStatus(StudyParticipant.Builder builder, Account account, CriteriaContext context) {
        Map<SubpopulationGuid, ConsentStatus> consentStatuses = consentService.getConsentStatuses(context, account);
        boolean isConsented = ConsentStatus.isUserConsented(consentStatuses);
        builder.withConsented(isConsented);
        return builder;
    }

    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        checkNotNull(study);
        
        Validate.entityThrowingException(new AccountSummarySearchValidator(study.getDataGroups()), search);
        
        return accountDao.getPagedAccountSummaries(study, search);
    }

    public void signUserOut(Study study, String email, boolean deleteReauthToken) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));

        AccountId accountId = AccountId.forEmail(study.getIdentifier(), email);
        Account account = getAccountThrowingException(accountId);

        if (deleteReauthToken) {
            accountDao.deleteReauthToken(accountId);
        }
        
        cacheProvider.removeSessionByUserId(account.getId());
    }

    /**
     * Create a study participant. A password must be provided, even if it is added on behalf of a user before
     * triggering a reset password request.
     */
    public IdentifierHolder createParticipant(Study study, StudyParticipant participant, boolean shouldSendVerification) {
        checkNotNull(study);
        checkNotNull(participant);
        
        if (study.getAccountLimit() > 0) {
            throwExceptionIfLimitMetOrExceeded(study);
        }
        
        StudyParticipantValidator validator = new StudyParticipantValidator(externalIdService, substudyService, study,
                true);
        Validate.entityThrowingException(validator, participant);
        
        // Set basic params from inputs.
        Account account = getAccount();
        account.setId(generateGUID());
        account.setStudyId(study.getIdentifier());
        account.setEmail(participant.getEmail());
        account.setPhone(participant.getPhone());
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setHealthCode(generateGUID());
        account.setExternalId(participant.getExternalId());
        account.setStatus(AccountStatus.UNVERIFIED);

        // Hash password if it has been supplied.
        if (participant.getPassword() != null) {
            try {
                PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
                String passwordHash = passwordAlgorithm.generateHash(participant.getPassword());
                account.setPasswordAlgorithm(passwordAlgorithm);
                account.setPasswordHash(passwordHash);
            } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new BridgeServiceException("Error creating password: " + ex.getMessage(), ex);
            }
        }
        updateAccountAndRoles(study, account, participant, true);

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
        
        // Set up the external ID object and the changes to the account, attempt to save the external ID 
        // within an account transaction, and roll back the account if the external ID save fails. If the 
        // account save fails, catch the exception and rollback the external ID save. 
        final ExternalIdentifier externalId = beginAssignExternalId(account, participant.getExternalId());
        try {
            accountDao.createAccount(study, account,
                    (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
        } catch(Exception e) {
            if (externalId != null) {
                externalIdService.unassignExternalId(account, externalId.getIdentifier());    
            }
            throw e;
        }
        
        // send verify email
        if (sendEmailVerification && !study.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(study, account.getId(), account.getEmail());
        }

        // If you create an account with a phone number, this opts the phone number in to receiving SMS. We do this
        // _before_ phone verification / sign-in, because we need to opt the user in to SMS in order to send phone
        // verification / sign-in.
        Phone phone = account.getPhone();
        if (phone != null) {
            // Note that there is no object with both accountId and phone, so we need to pass them in separately.
            smsService.optInPhoneNumber(account.getId(), phone);
        }

        // send verify phone number
        if (shouldSendVerification && !study.isAutoVerificationPhoneSuppressed()) {
            accountWorkflowService.sendPhoneVerificationToken(study, account.getId(), phone);
        }
        return new IdentifierHolder(account.getId());
    }
    
    // Provided to override in tests
    protected Account getAccount() {
        return Account.create();
    }
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
    }
    
    private boolean shouldEnableCompleteExternalIdAccount(StudyParticipant participant) {
        return participant.getEmail() == null && participant.getPhone() == null && 
            participant.getExternalId() != null && participant.getPassword() != null;
    }

    public void updateParticipant(Study study, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(participant);
        
        Account account = null;
        if (participant.getId() != null) {
            // Do not filter substudies because you are going to persist this account.
            // Only call this if participant has an ID. If it doesn't, it will fail
            // validation anyway and account will never be referenced.
            account = getAccountThrowingExceptionIfSubstudyMatches(
                    AccountId.forId(study.getIdentifier(), participant.getId()));
        }
        
        StudyParticipantValidator validator = new StudyParticipantValidator(
                externalIdService, substudyService, study, false);
        Validate.entityThrowingException(validator, participant);
        
        Set<Roles> callerRoles = BridgeUtils.getRequestContext().getCallerRoles();
        Set<String> allExternalIds = BridgeUtils.collectExternalIds(account);
        
        // Legacy behavior: a user can add an external ID to their account on an update, we refer to 
        // this as a "simple add." A researcher can assign an external ID to any user if it has not yet 
        // been assigned (if it has been assigned, beginAssignExternalId() throws an exception and 
        // aborts this entire call).
        
        boolean isSimpleAdd = allExternalIds.isEmpty() && participant.getExternalId() != null;
        boolean isResearcherAdd = callerRoles.contains(Roles.RESEARCHER)
                && !allExternalIds.contains(participant.getExternalId())
                && participant.getExternalId() != null;
        boolean assigningExternalId = isSimpleAdd || isResearcherAdd;
        
        // The last change made to the external ID collection is always reflected in the 
        // singular external ID field to ensure backwards compatibility during migration.
        if  (assigningExternalId) {
            account.setExternalId(participant.getExternalId());
        }
        updateAccountAndRoles(study, account, participant, false);
        
        // Allow admin and worker accounts to toggle status; in particular, to disable/enable accounts.
        if (participant.getStatus() != null) {
            if (callerRoles.contains(Roles.ADMIN) || callerRoles.contains(Roles.WORKER)) {
                account.setStatus(participant.getStatus());
            }
        }
        // Simple case, not trying to assign an external ID 
        if (!assigningExternalId) {
            accountDao.updateAccount(account, null);
            return;
        }
        
        // Complex case: you are assigning an external ID. Set up the external ID object and the changes
        // to the account, attempt to save the external ID within an account transaction, and roll back 
        // the account if the external ID save fails. If the account save fails, catch the exception and 
        // rollback the external ID save. 
        ExternalIdentifier externalId = beginAssignExternalId(account, participant.getExternalId());
        try {
            accountDao.updateAccount(account,
                    (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
        } catch (Exception e) {
            if (externalId != null) {
                externalIdService.unassignExternalId(account, externalId.getIdentifier());
            }
            throw e;
        }
    }

    private void throwExceptionIfLimitMetOrExceeded(Study study) {
        // It's sufficient to get minimum number of records, we're looking only at the total of all accounts
        PagedResourceList<AccountSummary> summaries = getPagedAccountSummaries(study, AccountSummarySearch.EMPTY_SEARCH);
        if (summaries.getTotal() >= study.getAccountLimit()) {
            throw new LimitExceededException(String.format(BridgeConstants.MAX_USERS_ERROR, study.getAccountLimit()));
        }
    }
    
    private void updateAccountAndRoles(Study study, Account account, StudyParticipant participant, boolean isNew) {
        account.setFirstName(participant.getFirstName());
        account.setLastName(participant.getLastName());
        account.setClientData(participant.getClientData());
        account.setSharingScope(participant.getSharingScope());
        account.setNotifyByEmail(participant.isNotifyByEmail());
        account.setDataGroups(participant.getDataGroups());
        account.setLanguages(participant.getLanguages());
        account.setMigrationVersion(AccountDao.MIGRATION_VERSION);
       
        // Only allow the setting of substudies on new accounts. Note that while administrators can change this 
        // after the account is created, for admin accounts, it can create some very strange security behavior 
        // for that account if it is signed in, so we MUST destroy the session. 
        Set<Roles> callerRoles = BridgeUtils.getRequestContext().getCallerRoles();
        if (isNew || callerRoles.contains(Roles.ADMIN)) {
            // Sign out the user if you make alterations that will change the security state of 
            // the account. Otherwise very strange bugs can results.
            boolean clearCache = false;
            
            // Copy to prevent concurrent modification exceptions
            Set<AccountSubstudy> accountSubstudies = ImmutableSet.copyOf(account.getAccountSubstudies());
            
            // remove external ID if it exists and unassign the external ID
            for (AccountSubstudy acctSubstudy : accountSubstudies) {
                if (!participant.getSubstudyIds().contains(acctSubstudy.getSubstudyId())) {
                    externalIdService.unassignExternalId(account, acctSubstudy.getExternalId());
                    account.getAccountSubstudies().remove(acctSubstudy);
                    clearCache = true;
                }
            }
            // add
            Set<String> existingSubstudyIds = account.getAccountSubstudies().stream()
                    .map(AccountSubstudy::getSubstudyId).collect(Collectors.toSet());
            for (String substudyId : participant.getSubstudyIds()) {
                if (!existingSubstudyIds.contains(substudyId)) {
                    AccountSubstudy newSubstudy = AccountSubstudy.create(
                            account.getStudyId(), substudyId, account.getId());
                    account.getAccountSubstudies().add(newSubstudy);
                    clearCache = true;
                }
            }
            
            // We have to clear the cache if we make changes that can alter the security profile of 
            // the account, otherwise very strange behavior can occur if that user is signed in with 
            // a stale session.
            if (!isNew && clearCache) {
                cacheProvider.removeSessionByUserId(account.getId());    
            }
        }
        // Do not copy timezone or external ID. Neither can be updated once set.
        
        for (String attribute : study.getUserProfileAttributes()) {
            String value = participant.getAttributes().get(attribute);
            account.getAttributes().put(attribute, value);
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
        final StudyIdentifier studyId = new StudyIdentifierImpl(account.getStudyId());
        
        return account.getConsentSignatureHistory(subpopGuid).stream().map(signature -> {
            Subpopulation subpop = subpopService.getSubpopulation(studyId, subpopGuid);
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
        smsService.sendSmsMessage(userId, builder.build());
    }
    
    public List<ActivityEvent> getActivityEvents(Study study, String userId) {
        Account account = getAccountThrowingException(study, userId);

        return activityEventService.getActivityEventList(account.getHealthCode());
    }
    
    public StudyParticipant updateIdentifiers(Study study, CriteriaContext context, IdentifierUpdate update) {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(update);
        
        // Validate
        Validate.entityThrowingException(new IdentifierUpdateValidator(study, externalIdService), update);
        
        // Sign in
        Account account;
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
        account = getAccountThrowingExceptionIfSubstudyMatches(AccountId.forId(study.getIdentifier(), account.getId()));
        
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
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        if (update.getExternalIdUpdate() != null && !externalIds.contains(update.getExternalIdUpdate())) {
            account.setExternalId(update.getExternalIdUpdate());
            accountUpdated = true;
            assignExternalId = true;
        }
        if (accountUpdated) {
            if (assignExternalId) {
                ExternalIdentifier externalId = beginAssignExternalId(account, account.getExternalId());
                try {
                    accountDao.updateAccount(account, (oneAccount) -> externalIdService.commitAssignExternalId(externalId));
                    updateRequestContext(externalId);
                } catch (Exception e) {
                    if (externalId != null) {
                        externalIdService.unassignExternalId(account, externalId.getIdentifier());
                    }
                    throw e;
                }                
            } else {
                accountDao.updateAccount(account, null);
            }
        }
        if (sendEmailVerification && 
            study.isEmailVerificationEnabled() && 
            !study.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(study, account.getId(), account.getEmail());
        }
        // return updated StudyParticipant to update and return session
        return getParticipant(study, account.getId(), false);
    }
    
    protected ExternalIdentifier beginAssignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkNotNull(account.getStudyId());
        checkNotNull(account.getHealthCode());
        
        if (externalId == null) {
            return null;
        }
        StudyIdentifier studyId = new StudyIdentifierImpl(account.getStudyId());
        
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(studyId, externalId);
        if (!optionalId.isPresent()) {
            return null;
        }
        ExternalIdentifier identifier = optionalId.get();
        if (identifier.getHealthCode() != null && !account.getHealthCode().equals(identifier.getHealthCode())) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", identifier.getIdentifier()); 
        }
        // Whether already assigned or not, we will adjust the account, in case we are repairing
        // an existing broken data association
        identifier.setHealthCode(account.getHealthCode());
        // For backwards compatibility while transitioning to multiple external IDs, assign the singular 
        // external ID field. But don't do this if we're adding a second external ID (we should be 
        // entirely migrated to multiple external ID usage before we need to assign multiple IDs).
        if (account.getExternalId() == null) {
            account.setExternalId(identifier.getIdentifier());    
        }
        if (identifier.getSubstudyId() != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getStudyId(),
                    identifier.getSubstudyId(), account.getId());
            acctSubstudy.setExternalId(identifier.getIdentifier());
            if (!account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().add(acctSubstudy);    
            }
        }
        return identifier;
    }


    /**
     * To see any new association to a substudy in the session that we return from the update identifiers call, 
     * we need to allow it in the permission structure of the call, which means we need to update the request 
     * context.
     */
    private void updateRequestContext(ExternalIdentifier externalId) {
        if (externalId.getSubstudyId() != null) {
            RequestContext currentContext = BridgeUtils.getRequestContext();
            
            Set<String> newSubstudies = new ImmutableSet.Builder<String>()
                    .addAll(currentContext.getCallerSubstudies())
                    .add(externalId.getSubstudyId()).build();
            
            RequestContext newContext = new RequestContext.Builder()
                    .withCallerRoles(currentContext.getCallerRoles())
                    .withCallerStudyId(currentContext.getCallerStudyIdentifier())
                    .withRequestId(currentContext.getId())
                    .withCallerSubstudies(newSubstudies)
                    .build();
            BridgeUtils.setRequestContext(newContext);
        }
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
            .withUserSubstudyIds(participant.getSubstudyIds())
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
    
    private Account getAccountThrowingExceptionIfSubstudyMatches(AccountId accountId) {
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
            boolean anyMatch = account.getAccountSubstudies().stream()
                    .anyMatch(as -> callerSubstudies.contains(as.getSubstudyId()));
            if (callerSubstudies.isEmpty() || anyMatch) {
                return account;
            }
        }
        throw new EntityNotFoundException(Account.class);
    }

    private Account getAccountThrowingException(Study study, String id) {
        return getAccountThrowingException(AccountId.forId(study.getIdentifier(), id));
    }
    
    private Account getAccountThrowingException(AccountId accountId) {
        Account account = BridgeUtils.filterForSubstudy(accountDao.getAccount(accountId));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }

}
