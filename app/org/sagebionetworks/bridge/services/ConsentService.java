package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
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
    private ParticipantOptionsService optionsService;
    private SendMailService sendMailService;
    private StudyConsentService studyConsentService;
    private ActivityEventService activityEventService;
    private SubpopulationService subpopService;
    private StudyService studyService;
    
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
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    };
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
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
        
        Account account = accountDao.getAccount(study, userId);
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
     * @param sendEmail
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
            ConsentSignature consentSignature, SharingScope sharingScope, boolean sendEmail) {
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
        Account account = accountDao.getAccount(study, participant.getId());
        ConsentSignature active = account.getActiveConsentSignature(subpopGuid);
        if (active != null && active.getConsentCreatedOn() == studyConsent.getCreatedOn()) {
            throw new EntityAlreadyExistsException(consentSignature);
        }

        // Add the consent creation timestamp and clear the withdrewOn timestamp, as some tests copy signatures
        // that contain this. As with all builders, order of with* calls matters here.
        ConsentSignature withConsentCreatedOnSignature = new ConsentSignature.Builder()
                .withConsentSignature(consentSignature).withWithdrewOn(null)
                .withConsentCreatedOn(studyConsent.getCreatedOn()).build();
        
        // Add consent signature to the list of signatures, save account.
        account.getConsentSignatureHistory(subpopGuid).add(withConsentCreatedOnSignature);
        accountDao.updateAccount(account);
        
        // Publish an enrollment event, set sharing scope 
        activityEventService.publishEnrollmentEvent(participant.getHealthCode(), withConsentCreatedOnSignature);
        optionsService.setEnum(study, participant.getHealthCode(), SHARING_SCOPE, sharingScope);
        
        // Send email, if required.
        if (sendEmail) {
            MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, participant.getEmail(),
                    withConsentCreatedOnSignature, sharingScope, studyConsent.getDocumentContent(), consentTemplate);

            sendMailService.sendEmail(consentEmail);
        }
    }

    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the study, and whether or not those 
     * consents are up-to-date.
     * @param context
     * @return
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context) {
        checkNotNull(context);
        
        Study study = studyService.getStudy(context.getStudyIdentifier());
        Account account = accountDao.getAccount(study, context.getUserId());
        
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<>();
        for (Subpopulation subpop : subpopService.getSubpopulationForUser(context)) {
            
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
     * @param study 
     * @param subpopGuid
     * @param participant
     * @param withdrawal
     * @param withdrewOn
     */
    public void withdrawConsent(Study study, SubpopulationGuid subpopGuid, StudyParticipant participant,
            Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkNotNull(participant);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);
        
        Account account = accountDao.getAccount(study, participant.getId());
        
        if(!withdrawSignatures(account, subpopGuid, withdrewOn)) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        accountDao.updateAccount(account);
        
        MimeTypeEmailProvider consentEmail = new WithdrawConsentEmailProvider(study, participant.getExternalId(),
                account, withdrawal, withdrewOn);
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
        
        for (SubpopulationGuid subpopGuid : account.getAllConsentSignatureHistories().keySet()) {
            withdrawSignatures(account, subpopGuid, withdrewOn);
        }
        accountDao.updateAccount(account);
        
        String externalId = optionsService.getOptions(account.getHealthCode()).getString(EXTERNAL_IDENTIFIER);
        MimeTypeEmailProvider consentEmail = new WithdrawConsentEmailProvider(study, externalId, account, withdrawal,
                withdrewOn);
        sendMailService.sendEmail(consentEmail);
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
        
        String htmlTemplate = studyConsentService.getActiveConsent(subpop).getDocumentContent();
        
        MimeTypeEmailProvider consentEmail = new ConsentEmailProvider(study, participant.getEmail(), consentSignature,
                sharingScope, htmlTemplate, consentTemplate);
        sendMailService.sendEmail(consentEmail);
    }

    private boolean withdrawSignatures(Account account, SubpopulationGuid subpopGuid, long withdrewOn) {
        boolean withdrewConsent = false;
        
        List<ConsentSignature> signatures = account.getConsentSignatureHistory(subpopGuid);
        // Withdraw every signature to this subpopulation that has not been withdrawn.
        for (ConsentSignature signature : signatures) {
            if (signature.getWithdrewOn() == null) {
                withdrewConsent = true;
                ConsentSignature withdrawn = new ConsentSignature.Builder()
                        .withConsentSignature(signature)
                        .withWithdrewOn(withdrewOn).build();
                int index = signatures.indexOf(signature);
                signatures.set(index, withdrawn);
            }
        }
        return withdrewConsent;
    }
}
