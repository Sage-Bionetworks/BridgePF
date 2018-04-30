package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.commaListToOrderedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.validators.ConsentAgeValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Methods to consent a user to one of the subpopulations of a study. After calling most of these methods, the user's
 * session should be updated.
 */
@Component
public class ConsentService {

    private AccountDao accountDao;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private ActivityEventService activityEventService;
    private SubpopulationService subpopService;
    private String xmlTemplateWithSignatureBlock;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.xmlTemplateWithSignatureBlock = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
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
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
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
        
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), userId));
        ConsentSignature signature = account.getActiveConsentSignature(subpopGuid);
        if (signature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);    
        }
        return signature;
    }
    
    /**
     * Consent this user to research. User will be updated to reflect consent. This method will ensure the 
     * user is not already consented to this subpopulation, but it does not validate that the user is a 
     * validate member of this subpopulation (that is checked in the controller).
     * 
     * @param study
     * @param subpopGuid
     * @param participant
     * @param consentSignature
     * @param sharingScope
     * @param sendSignedConsentToUser
     *      if true, send the consent document to the user's email address
     * @return
     * @throws EntityNotFoundException
     *      if the subpopulation is not part of the study
     * @throws InvalidEntityException
     *      if the user is not old enough to participate in the study (based on birthdate declared in signature)
     * @throws EntityAlreadyExistsException
     *      if the user has already signed the consent for this subpopulation
     */
    public void consentToResearch(Study study, SubpopulationGuid subpopGuid, StudyParticipant participant,
            ConsentSignature consentSignature, SharingScope sharingScope, boolean sendSignedConsentToUser) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(subpopGuid, Validate.CANNOT_BE_NULL, "subpopulationGuid");
        checkNotNull(participant, Validate.CANNOT_BE_NULL, "participant");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        ConsentAgeValidator validator = new ConsentAgeValidator(study);
        Validate.entityThrowingException(validator, consentSignature);

        Subpopulation subpop = subpopService.getSubpopulation(study.getStudyIdentifier(), subpopGuid);
        StudyConsentView studyConsent = studyConsentService.getActiveConsent(subpop);
        
        // If there's a signature to the current and active consent, user cannot consent again. They can sign
        // any other consent, including more recent consents.
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), participant.getId()));
        ConsentSignature active = account.getActiveConsentSignature(subpopGuid);
        if (active != null && active.getConsentCreatedOn() == studyConsent.getCreatedOn()) {
            throw new EntityAlreadyExistsException(ConsentSignature.class, null);
        }

        // Add the consent creation timestamp and clear the withdrewOn timestamp, as some tests copy signatures
        // that contain this. As with all builders, order of with* calls matters here.
        ConsentSignature withConsentCreatedOnSignature = new ConsentSignature.Builder()
                .withConsentSignature(consentSignature).withWithdrewOn(null)
                .withConsentCreatedOn(studyConsent.getCreatedOn()).build();
        
        // Add consent signature to the list of signatures, save account.
        List<ConsentSignature> consentListCopy = new ArrayList<>(account.getConsentSignatureHistory(subpopGuid));
        consentListCopy.add(withConsentCreatedOnSignature);
        account.setConsentSignatureHistory(subpopGuid, consentListCopy);
        account.setSharingScope(sharingScope);
        accountDao.updateAccount(account, false);
        
        // Publish an enrollment event, set sharing scope 
        activityEventService.publishEnrollmentEvent(study, participant.getHealthCode(), withConsentCreatedOnSignature);

        if (sendSignedConsentToUser) {
            ConsentPdf consentPdf = new ConsentPdf(study, participant.getTimeZone(), participant.getEmail(),
                    withConsentCreatedOnSignature, sharingScope, studyConsent.getDocumentContent(),
                    xmlTemplateWithSignatureBlock);
            
            BasicEmailProvider.Builder consentEmailBuilder = new BasicEmailProvider.Builder()
                    .withStudy(study)
                    .withEmailTemplate(study.getSignedConsentTemplate())
                    .withBinaryAttachment("consent.pdf", consentPdf.getBytes(), MimeType.JSON);
            addStudyConsentRecipients(study, consentEmailBuilder);
            // If the user's email exists and email notification is not suppressed, add the user to the provider
            String participantEmail = subpop.isAutoSendConsentSuppressed() ? null : participant.getEmail();
            if (participantEmail != null) {
                consentEmailBuilder.withRecipientEmail(participantEmail);
            }
            BasicEmailProvider provider = consentEmailBuilder.build();
            if (!provider.getRecipientEmails().isEmpty()) {
                sendMailService.sendEmail(provider);
            }
        }
    }

    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the study, and whether or not those 
     * consents are up-to-date.
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context) {
        checkNotNull(context);
        
        Account account = accountDao.getAccount(context.getAccountId());
        return getConsentStatuses(context, account);
    }
    
    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the study, and whether or not those 
     * consents are up-to-date. 
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context, Account account) {
        checkNotNull(context);
        
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<>();
        for (Subpopulation subpop : subpopService.getSubpopulationsForUser(context)) {
            
            ConsentSignature signature = account.getActiveConsentSignature(subpop.getGuid());
            boolean hasConsented = (signature != null);
            boolean hasSignedActiveConsent = (hasConsented && 
                    signature.getConsentCreatedOn() == subpop.getPublishedConsentCreatedOn());
            
            ConsentStatus status = new ConsentStatus.Builder().withName(subpop.getName())
                    .withGuid(subpop.getGuid()).withRequired(subpop.isRequired())
                    .withConsented(hasConsented).withSignedMostRecentConsent(hasSignedActiveConsent)
                    .build();
            builder.put(subpop.getGuid(), status);
        }
        return builder.build();
    }
    
    /**
     * Withdraw consent in this study. The withdrawal date is recorded and the user can no longer 
     * access any APIs that require consent, although the user's account (along with the history of 
     * the user's participation) will not be deleted.
     */
    public Map<SubpopulationGuid, ConsentStatus> withdrawConsent(Study study, SubpopulationGuid subpopGuid,
            StudyParticipant participant, CriteriaContext context, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(subpopGuid);
        checkNotNull(participant);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);
        
        Account account = accountDao.getAccount(context.getAccountId());

        if(!withdrawSignatures(account, subpopGuid, withdrewOn)) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        Map<SubpopulationGuid,ConsentStatus> statuses = getConsentStatuses(context, account);
        if (!ConsentStatus.isUserConsented(statuses)) {
            account.setSharingScope(SharingScope.NO_SHARING);
        }
        accountDao.updateAccount(account, false);
        
        sendWithdrawEmail(study, participant.getExternalId(), account, withdrawal, withdrewOn);

        return statuses;
    }
    
    /**
     * Withdraw user from any and all consents, and turn off sharing. Because a user's criteria for being included in a 
     * consent can change over time, this is really the best method for ensuring a user is withdrawn from everything. 
     * But in cases where there are studies with distinct and separate consents, you must selectively withdraw from 
     * the consent for a specific subpopulation.
     */
    public Map<SubpopulationGuid, ConsentStatus> withdrawAllConsents(Study study, StudyParticipant participant,
            CriteriaContext context, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        Account account = accountDao.getAccount(context.getAccountId());
        for (SubpopulationGuid subpopGuid : account.getAllConsentSignatureHistories().keySet()) {
            withdrawSignatures(account, subpopGuid, withdrewOn);
        }
        account.setSharingScope(SharingScope.NO_SHARING);
        accountDao.updateAccount(account, false);

        sendWithdrawEmail(study, participant.getExternalId(), account, withdrawal, withdrewOn);

        // But we don't need to query, we know these are all withdraw.
        return getConsentStatuses(context, account);
    }

    // Helper method, which abstracts away logic for sending withdraw notification email.
    private void sendWithdrawEmail(Study study, String externalId, Account account, Withdrawal withdrawal,
            long withdrewOn) {
        if (account.getEmail() == null) {
            // Withdraw email provider currently doesn't support non-email accounts. Skip.
            return;
        }
        if (study.isConsentNotificationEmailVerified() == Boolean.FALSE) {
            // For backwards-compatibility, a null value means the email is verified.
            return;
        }
        WithdrawConsentEmailProvider consentEmail = new WithdrawConsentEmailProvider(study, externalId, account,
                withdrawal, withdrewOn);
        if (!consentEmail.getRecipients().isEmpty()) {
            sendMailService.sendEmail(consentEmail);    
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

        ConsentSignature consentSignature = getConsentSignature(study, subpopGuid, participant.getId());
        SharingScope sharingScope = participant.getSharingScope();
        Subpopulation subpop = subpopService.getSubpopulation(study.getStudyIdentifier(), subpopGuid);
        
        String studyConsentDocument = studyConsentService.getActiveConsent(subpop).getDocumentContent();

        ConsentPdf consentPdf = new ConsentPdf(study, participant.getTimeZone(), participant.getEmail(),
                consentSignature, sharingScope, studyConsentDocument, xmlTemplateWithSignatureBlock);            
        
        BasicEmailProvider.Builder builder = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withEmailTemplate(study.getSignedConsentTemplate())
                .withBinaryAttachment("consent.pdf", consentPdf.getBytes(), MimeType.JSON)
                .withRecipientEmail(participant.getEmail());
        addStudyConsentRecipients(study, builder);
        // If the user doesn't have an email, we won't send anything. Note that as of this refactor, 
        // we're not verifying the email address. Change this when SMS is added.
        BasicEmailProvider provider = builder.build();
        if (!provider.getRecipientEmails().isEmpty()) {
            sendMailService.sendEmail(provider);
        }
    }
    
    private void addStudyConsentRecipients(Study study, BasicEmailProvider.Builder consentEmailBuilder) {
        Boolean consentNotificationEmailVerified = study.isConsentNotificationEmailVerified();
        if (consentNotificationEmailVerified == null || consentNotificationEmailVerified) {
            Set<String> studyRecipients = commaListToOrderedSet(study.getConsentNotificationEmail());
            for (String oneRecipient : studyRecipients) {
                consentEmailBuilder.withRecipientEmail(oneRecipient);
            }
        }
    }

    private boolean withdrawSignatures(Account account, SubpopulationGuid subpopGuid, long withdrewOn) {
        boolean withdrewConsent = false;
        
        List<ConsentSignature> signatures = account.getConsentSignatureHistory(subpopGuid);
        List<ConsentSignature> withdrawnSignatureList = new ArrayList<>();
        // Withdraw every signature to this subpopulation that has not been withdrawn.
        for (ConsentSignature signature : signatures) {
            if (signature.getWithdrewOn() == null) {
                withdrewConsent = true;
                ConsentSignature withdrawn = new ConsentSignature.Builder()
                        .withConsentSignature(signature)
                        .withWithdrewOn(withdrewOn).build();
                withdrawnSignatureList.add(withdrawn);
            } else {
                withdrawnSignatureList.add(signature);
            }
        }

        account.setConsentSignatureHistory(subpopGuid, withdrawnSignatureList);

        return withdrewConsent;
    }
}
