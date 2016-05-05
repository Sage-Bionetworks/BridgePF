package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope.NO_SHARING;

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("userAdminService")
public class UserAdminService {

    private AuthenticationService authenticationService;
    private AccountDao accountDao;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private HealthIdDao healthIdDao;
    private StudyService studyService;
    private SurveyResponseService surveyResponseService;
    private ScheduledActivityService scheduledActivityService;
    private ActivityEventService activityEventService;
    private CacheProvider cacheProvider;
    private ParticipantOptionsService optionsService;
    private ExternalIdService externalIdService;

    @Autowired
    public final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public final void setHealthIdDao(HealthIdDao healthIdDao) {
        this.healthIdDao = healthIdDao;
    }
    @Autowired
    public final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    public final void setSurveyResponseService(SurveyResponseService surveyResponseService) {
        this.surveyResponseService = surveyResponseService;
    }
    @Autowired
    public final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    public final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    public final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    /**
     * Create a user, sign that user is, and optionally consent that user to research. The method is idempotent: no
     * error occurs if the user exists, or is already signed in, or has already consented.
     * 
     * Note that currently, the ability to consent someone to a subpopulation other than the default 
     * subpopulation is not supported in the API.
     *
     * @param participant
     *            sign up information for the target user
     * @param study
     *            the study of the target user
     * @param subpopGuid
     *            the subpopulation to consent to (if null, it will use the default/study subpopulation).
     * @param signUserIn
     *            sign user into Bridge web application in as part of the creation process
     * @param consentUser
     *            should the user be consented to the research?
     * @return UserSession for the newly created user
     *
     * @throws BridgeServiceException
     */
    public UserSession createUser(StudyParticipant participant, Study study, SubpopulationGuid subpopGuid,
            boolean signUserIn, boolean consentUser) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(participant, "Participant cannot be null");
        checkNotNull(participant.getEmail(), "Sign up email cannot be null");

        authenticationService.signUp(study, participant, true);

        // We don't filter users by any of these filtering criteria in the admin API.
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(study.getStudyIdentifier()).build();
        
        UserSession newUserSession = null;
        try {
            SignIn signIn = new SignIn(participant.getEmail(), participant.getPassword());
            newUserSession = authenticationService.signIn(study, context, signIn);

            if (consentUser) {
                String name = String.format("[Signature for %s]", participant.getEmail());
                ConsentSignature signature = new ConsentSignature.Builder().withName(name)
                        .withBirthdate("1989-08-19").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
                
                User user = newUserSession.getUser();
                if (subpopGuid != null) {
                    consentService.consentToResearch(study, subpopGuid, user, signature, NO_SHARING, false);
                } else {
                    for (ConsentStatus consentStatus : user.getConsentStatuses().values()) {
                        if (consentStatus.isRequired()) {
                            SubpopulationGuid guid = SubpopulationGuid.create(consentStatus.getSubpopulationGuid());
                            consentService.consentToResearch(study, guid, user, signature, NO_SHARING, false);
                        }
                    }
                }
            }

            if (!signUserIn) {
                authenticationService.signOut(newUserSession);
                newUserSession.setAuthenticated(false);
            }
            return newUserSession;
        } catch (RuntimeException ex) {
            // Created the account, but failed to process the account properly. To avoid leaving behind a bunch of test
            // accounts, delete this account.
            if (newUserSession != null && newUserSession.getUser() != null) {
                deleteUser(study, newUserSession.getUser().getId());    
            }
            throw ex;
        }
    }

    /**
     * Delete the target user.
     *
     * @param id
     *            target user
     * @throws BridgeServiceException
     */
    public void deleteUser(Study study, String id) {
        checkNotNull(study);
        checkArgument(StringUtils.isNotBlank(id));
        Account account = accountDao.getAccount(study, id);
        if (account != null) {
            deleteUser(account);
        }
    }

    public void deleteAllUsers(Roles role) {
        Iterator<Account> iterator = accountDao.getAllAccounts();
        while(iterator.hasNext()) {
            Account account = iterator.next();
            if (account.getRoles().contains(role)) {
                deleteUser(account);
            }
        }
    }

    private void deleteUser(Account account) {
        checkNotNull(account);

        Study study = studyService.getStudy(account.getStudyIdentifier());
        // remove this first so if account is partially deleted, re-authenticating will pick
        // up accurate information about the state of the account (as we can recover it)
        cacheProvider.removeSessionByUserId(account.getId());
        if (account.getHealthId() != null) {
            // This is the fastest way to do this that I know of
            String healthCode = healthIdDao.getCode(account.getHealthId());
            // We expect to have health code, but when tests fail, we can get users who have signed in 
            // and do not have a health code.
            if (healthCode != null) {
                consentService.deleteAllConsentsForUser(study, healthCode);
                healthDataService.deleteRecordsForHealthCode(healthCode);
                scheduledActivityService.deleteActivitiesForUser(healthCode);
                activityEventService.deleteActivityEvents(healthCode);
                surveyResponseService.deleteSurveyResponses(healthCode);
                
                // Remove the externalId from the table even if validation is not enabled. If the study
                // turns it off/back on again, we want to track what has changed
                ParticipantOptionsLookup lookup = optionsService.getOptions(healthCode);
                String externalId = lookup.getString(EXTERNAL_IDENTIFIER);
                if (externalId != null) {
                    externalIdService.unassignExternalId(study, externalId, healthCode);    
                }
                optionsService.deleteAllParticipantOptions(healthCode);
            }
        }
        accountDao.deleteAccount(study, account.getId());
    }
}
