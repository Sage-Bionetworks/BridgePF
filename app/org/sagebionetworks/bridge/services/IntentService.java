package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.validators.IntentToParticipateValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

@Component
public class IntentService {

    private static final String APP_INSTALL_URL_KEY = "appInstallUrl";
    
    /** Hold on to the intent for 4 hours. */
    private static final int EXPIRATION_IN_SECONDS = 4 * 60 * 60;

    private SmsService smsService;
    
    private SendMailService sendMailService;

    private StudyService studyService;
    
    private SubpopulationService subpopService;
    
    private ConsentService consentService;
    
    private CacheProvider cacheProvider;

    private AccountDao accountDao;
    
    private ParticipantService participantService;

    /** SMS Service, used to send app install links via text message. */
    @Autowired
    final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }
    
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public void submitIntentToParticipate(IntentToParticipate intent) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, intent);
        
        // If the account exists, do nothing.
        AccountId accountId = null;
        if (intent.getPhone() != null) {
            accountId = AccountId.forPhone(intent.getStudyId(), intent.getPhone());
        } else {
            accountId = AccountId.forEmail(intent.getStudyId(), intent.getEmail());
        }
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            return;
        }
        
        // validate study exists
        Study study = studyService.getStudy(intent.getStudyId());

        // validate subpopulation exists
        SubpopulationGuid guid = SubpopulationGuid.create(intent.getSubpopGuid());
        subpopService.getSubpopulation(study, guid);
        
        // validate it has not yet been submitted
        // the validator has ensured that phone or email, but not both, have been provided;
        CacheKey cacheKey = (intent.getPhone() == null) ?
                CacheKey.itp(guid, study.getStudyIdentifier(), intent.getEmail()) :
                CacheKey.itp(guid, study.getStudyIdentifier(), intent.getPhone());

        if (cacheProvider.getObject(cacheKey, IntentToParticipate.class) == null) {
            cacheProvider.setObject(cacheKey, intent, EXPIRATION_IN_SECONDS);
            
            // send an app store link to download the app, if we have something to send.
            if (!study.getInstallLinks().isEmpty()) {
                String url = getInstallLink(intent.getOsName(), study.getInstallLinks());
                
                if (intent.getPhone() != null) {
                    // The URL being sent does not expire. We send with a transaction delivery type because
                    // this is a critical step in onboarding through this workflow and message needs to be 
                    // sent immediately after consenting.
                    SmsMessageProvider provider = new SmsMessageProvider.Builder()
                            .withStudy(study)
                            .withSmsTemplate(study.getAppInstallLinkSmsTemplate())
                            .withTransactionType()
                            .withPhone(intent.getPhone())
                            .withToken(APP_INSTALL_URL_KEY, url).build();
                    // Account hasn't been created yet, so there is no ID yet. Pass in null user ID to
                    // SMS Service.
                    smsService.sendSmsMessage(null, provider);
                } else {
                    BasicEmailProvider provider = new BasicEmailProvider.Builder()
                            .withStudy(study)
                            .withEmailTemplate(study.getAppInstallLinkTemplate())
                            .withRecipientEmail(intent.getEmail())
                            .withType(EmailType.APP_INSTALL)
                            .withToken(APP_INSTALL_URL_KEY, url)
                            .build();
                    sendMailService.sendEmail(provider);
                }
            }
        }
    }
    
    public boolean registerIntentToParticipate(Study study, Account account) {
        Phone phone = account.getPhone();
        String email = account.getEmail();
        // Somehow, this is being called but the user has no phone number.
        if (phone == null && email == null) {
            return false;
        }
        boolean consentsUpdated = false;
        StudyParticipant participant = null;
        List<Subpopulation> subpops = subpopService.getSubpopulations(study.getStudyIdentifier(), false);
        for (Subpopulation subpop : subpops) {
            CacheKey cacheKey = (phone == null) ?
                    CacheKey.itp(subpop.getGuid(), study.getStudyIdentifier(), email) :
                    CacheKey.itp(subpop.getGuid(), study.getStudyIdentifier(), phone);
            IntentToParticipate intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (intent != null) {
                if (participant == null) {
                    participant = participantService.getParticipant(study, account.getId(), true);
                }
                consentService.consentToResearch(study, subpop.getGuid(), participant, 
                        intent.getConsentSignature(), intent.getScope(), true);
                cacheProvider.removeObject(cacheKey);
                consentsUpdated = true;
            }
        }
        return consentsUpdated;
    }
    
    protected String getInstallLink(String osName, Map<String,String> installLinks) {
        String installLink = installLinks.get(osName);
        // OS name wasn't submitted or it's wrong, use the universal link
        if (installLink == null) {
            installLink = installLinks.get(OperatingSystem.UNIVERSAL);
        }
        // Don't have a link named "Universal" so just find ANYTHING
        if (installLink == null && !installLinks.isEmpty()) {
            installLink = Iterables.getFirst(installLinks.values(), null);
        }
        return installLink;
    }
}
