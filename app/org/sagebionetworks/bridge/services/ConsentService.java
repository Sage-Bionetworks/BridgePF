package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.commaListToOrderedSet;
import static org.sagebionetworks.bridge.BridgeConstants.EXPIRATION_PERIOD_KEY;
import static org.sagebionetworks.bridge.BridgeConstants.SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
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
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.ConsentSignatureValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.HttpMethod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Methods to consent a user to one of the subpopulations of a study. After calling most of these methods, the user's
 * session should be updated.
 */
@Component
public class ConsentService {
    
    protected static final String USERSIGNED_CONSENTS_BUCKET = BridgeConfigFactory.getConfig().get("usersigned.consents.bucket");
    private AccountDao accountDao;
    private SendMailService sendMailService;
    private SmsService smsService;
    private NotificationsService notificationsService;
    private StudyConsentService studyConsentService;
    private ActivityEventService activityEventService;
    private SubpopulationService subpopService;
    private String xmlTemplateWithSignatureBlock;
    private S3Helper s3Helper;
    private UrlShortenerService urlShortenerService;
    
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

    /** SMS Service, used to send consent via text message. */
    @Autowired
    final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
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
    @Resource(name = "s3Helper")
    final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }
    @Autowired
    final void setUrlShortenerService(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }
    
    /**
     * Get the user's active consent signature (a signature that has not been withdrawn).
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
     * validate member of this subpopulation (that is checked in the controller). Will optionally send 
     * a signed copy of the consent to the user via email or phone (whichever is verified).
     * 
     * @param sendSignedConsent
     *      if true, send the consent document to the user's email address
     * @throws EntityNotFoundException
     *      if the subpopulation is not part of the study
     * @throws InvalidEntityException
     *      if the user is not old enough to participate in the study (based on birthdate declared in signature)
     * @throws EntityAlreadyExistsException
     *      if the user has already signed the consent for this subpopulation
     */
    public void consentToResearch(Study study, SubpopulationGuid subpopGuid, StudyParticipant participant,
            ConsentSignature consentSignature, SharingScope sharingScope, boolean sendSignedConsent) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(subpopGuid, Validate.CANNOT_BE_NULL, "subpopulationGuid");
        checkNotNull(participant, Validate.CANNOT_BE_NULL, "participant");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        ConsentSignatureValidator validator = new ConsentSignatureValidator(study.getMinAgeOfConsent());
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
        accountDao.updateAccount(account);
        
        // Publish an enrollment event, set sharing scope 
        activityEventService.publishEnrollmentEvent(study, participant.getHealthCode(), withConsentCreatedOnSignature);

        // Administrative actions, almost exclusively for testing, will send no consent documents
        if (sendSignedConsent) {
            ConsentPdf consentPdf = new ConsentPdf(study, participant, withConsentCreatedOnSignature, sharingScope,
                    studyConsent.getDocumentContent(), xmlTemplateWithSignatureBlock);
            
            boolean verifiedEmail = (participant.getEmail() != null
                    && Boolean.TRUE.equals(participant.getEmailVerified()));
            boolean verifiedPhone = (participant.getPhone() != null
                    && Boolean.TRUE.equals(participant.getPhoneVerified()));
            
            // Send an email to the user if they have an email address and we're not suppressing the send, 
            // and/or to any study consent administrators.
            Set<String> recipientEmails = Sets.newHashSet();
            if (verifiedEmail && !subpop.isAutoSendConsentSuppressed()) {
                recipientEmails.add(participant.getEmail());    
            }
            addStudyConsentRecipients(study, recipientEmails);
            if (!recipientEmails.isEmpty()) {
                BasicEmailProvider.Builder consentEmailBuilder = new BasicEmailProvider.Builder()
                        .withStudy(study)
                        .withEmailTemplate(study.getSignedConsentTemplate())
                        .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                        .withType(EmailType.SIGN_CONSENT);
                for (String recipientEmail : recipientEmails) {
                    consentEmailBuilder.withRecipientEmail(recipientEmail);
                }
                sendMailService.sendEmail(consentEmailBuilder.build());
            }
            // Otherwise if there's no verified email but there is a phone and we're not suppressing, send it there
            if (!subpop.isAutoSendConsentSuppressed() && !verifiedEmail && verifiedPhone) {
                sendConsentViaSMS(study, subpop, participant, consentPdf);    
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
            notificationsService.deleteAllRegistrations(study.getStudyIdentifier(), participant.getHealthCode());
            account.setSharingScope(SharingScope.NO_SHARING);
        }
        accountDao.updateAccount(account);
        
        sendWithdrawEmail(study, participant.getExternalId(), account, withdrawal, withdrewOn);

        return statuses;
    }
    
    /**
     * Withdraw user from any and all consents, turn off sharing, unregister the device from any notifications, and 
     * delete the identifiers of the account. Because a user's criteria for being included in a consent can change 
     * over time, this is really the best method for ensuring a user is withdrawn from everything. But in cases where 
     * there are studies with distinct and separate consents, you can also selectively withdraw from the consent for 
     * a specific subpopulation without dropping out of the study.
     */
    public void withdrawFromStudy(Study study, StudyParticipant participant, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        AccountId accountId = AccountId.forId(study.getIdentifier(), participant.getId());
        Account account = accountDao.getAccount(accountId);
        for (SubpopulationGuid subpopGuid : account.getAllConsentSignatureHistories().keySet()) {
            withdrawSignatures(account, subpopGuid, withdrewOn);
        }
        sendWithdrawEmail(study, participant.getExternalId(), account, withdrawal, withdrewOn);
        
        // Forget this person. If the user registers again at a later date, it is as if they have created
        // a new account. But we hold on to this record so we can still retrieve the consent records for a 
        // given healthCode.
        account.setSharingScope(SharingScope.NO_SHARING);
        account.setNotifyByEmail(false);
        account.setEmail(null);
        account.setEmailVerified(false);
        account.setPhone(null);
        account.setPhoneVerified(false);
        account.setExternalId(null);
        accountDao.updateAccount(account);

        notificationsService.deleteAllRegistrations(study.getStudyIdentifier(), participant.getHealthCode());
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
     * Resend the participant's signed consent agreement via the user's email address or their phone number. 
     * It is an error to call this method if no channel exists to send the consent to the user.
     */
    public void resendConsentAgreement(Study study, SubpopulationGuid subpopGuid, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkNotNull(participant);

        ConsentSignature consentSignature = getConsentSignature(study, subpopGuid, participant.getId());
        SharingScope sharingScope = participant.getSharingScope();
        Subpopulation subpop = subpopService.getSubpopulation(study.getStudyIdentifier(), subpopGuid);
        String studyConsentDocument = studyConsentService.getActiveConsent(subpop).getDocumentContent();

        boolean verifiedEmail = (participant.getEmail() != null
                && Boolean.TRUE.equals(participant.getEmailVerified()));
        boolean verifiedPhone = (participant.getPhone() != null
                && Boolean.TRUE.equals(participant.getPhoneVerified()));
        
        ConsentPdf consentPdf = new ConsentPdf(study, participant, consentSignature, sharingScope, studyConsentDocument,
                xmlTemplateWithSignatureBlock);
        
        if (verifiedEmail) {
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                    .withStudy(study)
                    .withEmailTemplate(study.getSignedConsentTemplate())
                    .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                    .withRecipientEmail(participant.getEmail())
                    .withType(EmailType.RESEND_CONSENT).build();
            sendMailService.sendEmail(provider);
        } else if (verifiedPhone) {
            sendConsentViaSMS(study, subpop, participant, consentPdf);
        } else {
            throw new BadRequestException("Participant does not have a valid email address or phone number");
        }
    }
    
    private void sendConsentViaSMS(Study study, Subpopulation subpop, StudyParticipant participant,
            ConsentPdf consentPdf) {
        String shortUrl;
        try {
            String fileName = getSignedConsentUrl();
            DateTime expiresOn = getDownloadExpiration();
            s3Helper.writeBytesToS3(USERSIGNED_CONSENTS_BUCKET, fileName, consentPdf.getBytes());
            URL url = s3Helper.generatePresignedUrl(USERSIGNED_CONSENTS_BUCKET, fileName, expiresOn, HttpMethod.GET);
            shortUrl = urlShortenerService.shortenUrl(url.toString(), SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS);
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withPhone(participant.getPhone())
                .withExpirationPeriod(EXPIRATION_PERIOD_KEY, SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS)
                .withTransactionType()
                .withSmsTemplate(study.getSignedConsentSmsTemplate())
                .withToken(BridgeConstants.CONSENT_URL, shortUrl)
                .build();
        smsService.sendSmsMessage(participant.getHealthCode(), provider);
    }
    
    protected String getSignedConsentUrl() {
        return SecureTokenGenerator.INSTANCE.nextToken() + ".pdf";
    }
    
    protected DateTime getDownloadExpiration() {
        return DateUtils.getCurrentDateTime().plusSeconds(SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS);
    }
    
    private void addStudyConsentRecipients(Study study, Set<String> recipientEmails) {
        Boolean consentNotificationEmailVerified = study.isConsentNotificationEmailVerified();
        if (consentNotificationEmailVerified == null || consentNotificationEmailVerified) {
            Set<String> studyRecipients = commaListToOrderedSet(study.getConsentNotificationEmail());
            recipientEmails.addAll(studyRecipients);
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
