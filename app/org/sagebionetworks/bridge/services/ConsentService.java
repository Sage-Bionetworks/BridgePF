package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsentService {

    private final Logger LOGGER = LoggerFactory.getLogger(ConsentService.class);

    private AccountDao accountDao;
    private ParticipantOptionsService optionsService;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private UserConsentDao userConsentDao;
    private ActivityEventService activityEventService;
    private SubpopulationService subpopService;
    
    private String consentTemplate;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.consentTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Resource(name="stormpathAccountDao")
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    @Autowired
    final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    @Autowired
    final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    };
    
    /**
     * Get the user's active consent signature (a signature that has not been withdrawn).
     * @param study
     * @param subpopGuid
     * @param userId
     * @return
     * @throws EntityNotFoundException if no consent exists
     */
    public ConsentSignature getConsentSignature(Study study, SubpopulationGuid subpopGuid, String userId) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkNotNull(userId);
        
        // This will throw an EntityNotFoundException if the subpopulation is not in the user's study
        subpopService.getSubpopulation(study, subpopGuid);
        
        Account account = accountDao.getAccount(study, userId);
        ConsentSignature signature = account.getActiveConsentSignature(subpopGuid);
        if (signature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);    
        }
        return signature;
    }
    
    /**
     * Consent this user to research. User will be updated to reflect consent.
     * @param study
     * @param subpopGuid
     * @param session
     * @param consentSignature
     * @param sharingScope
     * @param sendEmail
     * @return
     * @throws EntityAlreadyExistsException
     *      if the user already has an active consent to participate in research
     * @throws StudyLimitExceededException
     *      if enrolling the user would exceed the study enrollment limit
     */
    public void consentToResearch(Study study, SubpopulationGuid subpopGuid, UserSession session, ConsentSignature consentSignature, 
            SharingScope sharingScope, boolean sendEmail) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(subpopGuid, Validate.CANNOT_BE_NULL, "subpopulationGuid");
        checkNotNull(session, Validate.CANNOT_BE_NULL, "session");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        ConsentStatus status = session.getConsentStatuses().get(subpopGuid);
        // There will be a status object for each subpopulation the user is mapped to. If 
        // there's no status object, then in effect the subpopulation does not exist for 
        // this user and they should get back a 404.
        if (status == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        if (status != null && status.isConsented()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }
        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        StudyConsentView studyConsent = studyConsentService.getActiveConsent(subpopGuid);
        Account account = accountDao.getAccount(study, session.getParticipant().getId());
        
        account.getConsentSignatureHistory(subpopGuid).add(consentSignature);
        accountDao.updateAccount(account);
        
        UserConsent userConsent = null;
        try {
            userConsent = userConsentDao.giveConsent(session.getHealthCode(), subpopGuid,
                    studyConsent.getCreatedOn(), consentSignature.getSignedOn());
        } catch (Throwable e) {
            int len = account.getConsentSignatureHistory(subpopGuid).size();
            account.getConsentSignatureHistory(subpopGuid).remove(len-1);
            accountDao.updateAccount(account);
            throw e;
        }
        // Save supplemental records, fire events, etc.
        if (userConsent != null){
            activityEventService.publishEnrollmentEvent(session.getParticipant().getHealthCode(), userConsent);
        }
        optionsService.setEnum(study, session.getParticipant().getHealthCode(), SHARING_SCOPE, sharingScope);
        
        updateSessionConsentStatuses(session, subpopGuid, true);
        
        if (sendEmail) {
            MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, subpopGuid,
                    session.getParticipant().getEmail(), consentSignature, sharingScope, studyConsentService,
                    consentTemplate);

            sendMailService.sendEmail(consentEmail);
        }
    }

    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the study, whether or not those 
     * consents are up-to-date, etc.
     * @param context
     * @return
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context) {
        checkNotNull(context);
        checkNotNull(context.getHealthCode());
        
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<>();
        for (Subpopulation subpop : subpopService.getSubpopulationForUser(context)) {
            boolean consented = userConsentDao.hasConsented(context.getHealthCode(), subpop.getGuid());
            boolean mostRecent = hasUserSignedActiveConsent(context.getHealthCode(), subpop.getGuid());            
            ConsentStatus status = new ConsentStatus.Builder().withName(subpop.getName())
                    .withGuid(subpop.getGuid()).withRequired(subpop.isRequired())
                    .withConsented(consented).withSignedMostRecentConsent(mostRecent)
                    .build();
            builder.put(subpop.getGuid(), status);
        }
        return builder.build();
    }

    /**
     * Withdraw consent in this study. The withdrawal date is recorded and the user can no longer 
     * access any APIs that require consent, although the user's account (along with the history of 
     * the user's participation) will not be deleted.
     * @param study 
     * @param subpopGuid
     * @param user
     * @param withdrawal
     * @param withdrewOn
     */
    public void withdrawConsent(Study study, SubpopulationGuid subpopGuid, UserSession session, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkNotNull(session);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);
        
        Account account = accountDao.getAccount(study, session.getParticipant().getId());
        
        List<ConsentSignature> signatures = account.getConsentSignatureHistory(subpopGuid);

        boolean noSignedConsent = true;
        for (ConsentSignature aSignature: signatures) {
            if (aSignature.getWithdrewOn() == null) {
                noSignedConsent = false;
                ConsentSignature withdrawn = new ConsentSignature.Builder()
                        .withConsentSignature(aSignature)
                        .withWithdrewOn(withdrewOn).build();
                    int index = signatures.indexOf(aSignature); // should be length-1
                    signatures.set(index, withdrawn);
            }
        }
        if(noSignedConsent) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        
        accountDao.updateAccount(account);
        try {
            userConsentDao.withdrawConsent(session.getParticipant().getHealthCode(), subpopGuid, withdrewOn);    
        } catch(Exception e) {
            LOGGER.error("Error updating UserConsent table (repair code will attempt to reconcile this the next time the user authenticates.", e);
        }

        // Update the user's consent status
        updateSessionConsentStatuses(session, subpopGuid, false);
        
        // Only turn of sharing if the upshot of your change in consents is that you are not consented.
        if (!session.doesConsent()) {
            optionsService.setEnum(study, session.getParticipant().getHealthCode(), SHARING_SCOPE, SharingScope.NO_SHARING);
            
            StudyParticipant participant = new StudyParticipant.Builder().copyOf(session.getParticipant())
                    .withSharingScope(SharingScope.NO_SHARING).build();
            session.setParticipant(participant);
        }
        
        String externalId = session.getParticipant().getExternalId();
        MimeTypeEmailProvider consentEmail = new WithdrawConsentEmailProvider(study, externalId, account, withdrawal,
                withdrewOn);
        sendMailService.sendEmail(consentEmail);
    }
    
    /**
     * Withdraw user from any and all consents, and turn off sharing. Because a user's criteria for being included in a 
     * consent can change over time, this is really the best method for ensuring a user is withdrawn from everything. 
     * But in cases where there are studies with distinct and separate consents, you must selectively withdraw from 
     * the consent for a specific subpopulation. Note that this method assumes it is known that the the userId will 
     * return an account.
     * 
     * @param study
     * @param userId
     * @param withdrawal
     * @param withdrewOn
     */
    public void withdrawAllConsents(Study study, String userId, Withdrawal withdrawal, long withdrewOn) {
        Account account = accountDao.getAccount(study, userId);
        withdrawAllConsents(study, account, withdrawal, withdrewOn);
    }
    
    /**
     * Withdraw user from any and all consents, and turn off sharing. Because a user's criteria for being included in a 
     * consent can change over time, this is really the best method for ensuring a user is withdrawn from everything. 
     * But in cases where there are studies with distinct and separate consents, you must selectively withdraw from 
     * the consent for a specific subpopulation.
     * 
     * @param study
     * @param account
     * @param withdrawal
     * @param withdrewOn
     */
    public void withdrawAllConsents(Study study, Account account, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(account);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        // Do this first, as it directly impacts the export of data, and if nothing else, we'd like this to succeed.
        optionsService.setEnum(study.getStudyIdentifier(), account.getHealthCode(), SHARING_SCOPE, SharingScope.NO_SHARING);
        
        Set<SubpopulationGuid> updates = Sets.newHashSet();
        for (SubpopulationGuid subpopGuid : account.getAllConsentSignatureHistories().keySet()) {
            List<ConsentSignature> signatures = account.getConsentSignatureHistory(subpopGuid);
            
            for (ConsentSignature aSignature : signatures) {
                if (aSignature.getWithdrewOn() == null) {
                    // Withdraw this signature. There should be only one, but I want to be absolutely sure.
                    ConsentSignature withdrawn = new ConsentSignature.Builder()
                            .withConsentSignature(aSignature)
                            .withWithdrewOn(withdrewOn).build();
                    int index = signatures.indexOf(aSignature);
                    signatures.set(index, withdrawn);
                    updates.add(subpopGuid);
                }
            }
        }
        accountDao.updateAccount(account);
        for (SubpopulationGuid subpopGuid : updates) {
            userConsentDao.withdrawConsent(account.getHealthCode(), subpopGuid, withdrewOn);    
        }
        String externalId = optionsService.getOptions(account.getHealthCode()).getString(EXTERNAL_IDENTIFIER);
        
        MimeTypeEmailProvider consentEmail = new WithdrawConsentEmailProvider(
                study, externalId, account, withdrawal, withdrewOn);
        sendMailService.sendEmail(consentEmail);
        
        // NOW, verify that the all consents have been withdrawn, because this is failing on staging
        Account retrievedAccount = accountDao.getAccount(study, account.getId());
        retrievedAccount.getAllConsentSignatureHistories();
        for (SubpopulationGuid subpopGuid : retrievedAccount.getAllConsentSignatureHistories().keySet()) {
            List<ConsentSignature> signatures = retrievedAccount.getConsentSignatureHistory(subpopGuid);

            for (ConsentSignature aSignature : signatures) {
                if (aSignature.getWithdrewOn() == null) {
                    throw new BridgeServiceException("Consistency error, " + account.getId() + " has ConsentSignature that was not withdrawn.");
                }
                UserConsent consentRecord = userConsentDao.getUserConsent(account.getHealthCode(), subpopGuid, aSignature.getSignedOn());
                if (consentRecord.getWithdrewOn() == null) {
                    throw new BridgeServiceException("Consistency error, " + account.getId() + " has UserConsent record that was not withdrawn.");
                }
            }
        }
    }
    
    /**
     * Get a history of all consent records, whether withdrawn or not, including information from the 
     * consent signature and user consent records. The information is sufficient to identify the 
     * consent that exists for a healthCode, and to retrieve the version of the consent the participant 
     * signed to join the study.
     * @param study
     * @param subpopGuid
     * @param healthCode
     * @param email
     */
    public List<UserConsentHistory> getUserConsentHistory(Study study, SubpopulationGuid subpopGuid, String healthCode, String id) {
        Account account = accountDao.getAccount(study, id);
        
        return account.getConsentSignatureHistory(subpopGuid).stream().map(signature -> {
            UserConsent consent = userConsentDao.getUserConsent(
                    healthCode, subpopGuid, signature.getSignedOn());
            boolean hasSignedActiveConsent = hasUserSignedActiveConsent(healthCode, subpopGuid);
            
            UserConsentHistory.Builder builder = new UserConsentHistory.Builder();
            builder.withName(signature.getName())
                .withSubpopulationGuid(SubpopulationGuid.create(consent.getSubpopulationGuid()))
                .withBirthdate(signature.getBirthdate())
                .withImageData(signature.getImageData())
                .withImageMimeType(signature.getImageMimeType())
                .withSignedOn(signature.getSignedOn())
                .withHealthCode(healthCode)
                .withWithdrewOn(consent.getWithdrewOn())
                .withConsentCreatedOn(consent.getConsentCreatedOn())
                .withHasSignedActiveConsent(hasSignedActiveConsent);
            return builder.build();
        }).collect(BridgeCollectors.toImmutableList());
    }
    
    /**
     * Delete all consent records, withdrawn or active, in the process of deleting a user account. This is 
     * used for tests, do not call this method to withdraw a user from a study, or we will not have auditable 
     * records about their participation.
     * @param study
     * @param healthCode
     */
    public void deleteAllConsentsForUser(Study study, String healthCode) {
        checkNotNull(study);
        checkNotNull(healthCode);
        
        // May exceed the subpopulations the user matches, but that's okay
        List<Subpopulation> subpopGuids = subpopService.getSubpopulations(study);
        for (Subpopulation subpop : subpopGuids) {
            userConsentDao.deleteAllConsents(healthCode, subpop.getGuid());    
        }
    }
    
    /**
     * Email the participant's signed consent agreement to the user's email address.
     * @param study
     * @param subpopGuid
     * @param participant
     */
    public void emailConsentAgreement(Study study, SubpopulationGuid subpopGuid, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkNotNull(participant);

        final ConsentSignature consentSignature = getConsentSignature(study, subpopGuid, participant.getId());
        if (consentSignature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        final SharingScope sharingScope = optionsService.getOptions(participant.getHealthCode())
                .getEnum(SHARING_SCOPE, SharingScope.class);
        
        MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, subpopGuid,
                participant.getEmail(), consentSignature, sharingScope, studyConsentService,
                consentTemplate);
        sendMailService.sendEmail(consentEmail);
    }
    
    private boolean hasUserSignedActiveConsent(String healthCode, SubpopulationGuid subpopGuid) {
        checkNotNull(healthCode);
        checkNotNull(subpopGuid);
        
        UserConsent userConsent = userConsentDao.getActiveUserConsent(healthCode, subpopGuid);
        StudyConsentView mostRecentConsent = studyConsentService.getActiveConsent(subpopGuid);
        
        if (mostRecentConsent != null && userConsent != null) {
            return userConsent.getConsentCreatedOn() == mostRecentConsent.getCreatedOn();
        } else {
            return false;
        }
    }
    
    private void updateSessionConsentStatuses(UserSession session, SubpopulationGuid subpopGuid, boolean consented) {
        if (session.getConsentStatuses() != null) {
            ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<>();
            for (Map.Entry<SubpopulationGuid,ConsentStatus> entry : session.getConsentStatuses().entrySet()) {
                if (entry.getKey().equals(subpopGuid)) {
                    ConsentStatus updatedStatus = new ConsentStatus.Builder().withConsentStatus(entry.getValue())
                            .withConsented(consented).withSignedMostRecentConsent(consented).build();
                    builder.put(subpopGuid, updatedStatus);
                } else {
                    builder.put(entry.getKey(), entry.getValue());
                }
            }
            session.setConsentStatuses(builder.build());
        }
    }
}
