package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope.NO_SHARING;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("userAdminService")
public class UserAdminService {
    
    private static final Set<Roles> ADMIN_ROLE = Sets.newHashSet(Roles.ADMIN);

    private AuthenticationService authenticationService;
    private ParticipantService participantService;
    private AccountDao accountDao;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private ScheduledActivityService scheduledActivityService;
    private ActivityEventService activityEventService;
    private CacheProvider cacheProvider;
    private ParticipantOptionsService optionsService;
    private ExternalIdService externalIdService;

    @Autowired
    final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
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
    final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    /**
     * Create a user and optionally consent the user and/or sign the user in. If a specific subpopulation 
     * is not specified (and currently the API for this method does not allow it), than the method iterates 
     * through all subpopulations in the study and consents the user to all required consents. This should 
     * allow the user to make calls without receiving a 412 response. 
     * 
     * @param study
     *            the study of the target user
     * @param participant
     *            sign up information for the target user
     * @param subpopGuid
     *            the subpopulation to consent to (if null, it will use the default/study subpopulation).
     * @param signUserIn
     *            should the user be signed into Bridge after creation?
     * @param consentUser
     *            should the user be consented to the research?
     *
     * @return UserSession for the newly created user
     */
    public UserSession createUser(Study study, StudyParticipant participant, SubpopulationGuid subpopGuid,
            boolean signUserIn, boolean consentUser) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(participant, "Participant cannot be null");
        checkNotNull(participant.getEmail(), "Sign up email cannot be null");
        
        IdentifierHolder identifier = participantService.createParticipant(study, ADMIN_ROLE, participant, false);

        // We don't filter users by any of these filtering criteria in the admin API.
        CriteriaContext context = new CriteriaContext.Builder()
                .withUserId(identifier.getIdentifier())
                .withStudyIdentifier(study.getStudyIdentifier()).build();
        
        UserSession newUserSession = null;
        try {
            SignIn signIn = new SignIn(participant.getEmail(), participant.getPassword());
            newUserSession = authenticationService.signIn(study, context, signIn);
            
            if (consentUser) {
                String name = String.format("[Signature for %s]", participant.getEmail());
                ConsentSignature signature = new ConsentSignature.Builder().withName(name)
                        .withBirthdate("1989-08-19").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
                
                if (subpopGuid != null) {
                    consentService.consentToResearch(study, subpopGuid, newUserSession.getParticipant(), signature, NO_SHARING, false);
                } else {
                    for (ConsentStatus consentStatus : newUserSession.getConsentStatuses().values()) {
                        if (consentStatus.isRequired()) {
                            SubpopulationGuid guid = SubpopulationGuid.create(consentStatus.getSubpopulationGuid());
                            consentService.consentToResearch(study, guid, newUserSession.getParticipant(), signature, NO_SHARING, false);
                        }
                    }
                }
                newUserSession = authenticationService.getSession(study, context);
            }
            if (!signUserIn) {
                authenticationService.signOut(newUserSession);
                newUserSession.setAuthenticated(false);
            }
            return newUserSession;
        } catch (RuntimeException ex) {
            // Created the account, but failed to process the account properly. To avoid leaving behind a bunch of test
            // accounts, delete this account.
            if (newUserSession != null) {
                deleteUser(study, newUserSession.getId());    
            }
            throw ex;
        }
    }

    /**
     * Delete the target user.
     *
     * @param study
     *      target user's study
     * @param id
     *      target user's ID
     */
    public void deleteUser(Study study, String id) {
        checkNotNull(study);
        checkArgument(StringUtils.isNotBlank(id));
        
        Account account = accountDao.getAccount(study, id);
        if (account != null) {
            // remove this first so if account is partially deleted, re-authenticating will pick
            // up accurate information about the state of the account (as we can recover it)
            cacheProvider.removeSessionByUserId(account.getId());
            
            String healthCode = account.getHealthCode();
            healthDataService.deleteRecordsForHealthCode(healthCode);
            scheduledActivityService.deleteActivitiesForUser(healthCode);
            activityEventService.deleteActivityEvents(healthCode);
            
            // Remove the externalId from the table even if validation is not enabled. If the study
            // turns it off/back on again, we want to track what has changed
            ParticipantOptionsLookup lookup = optionsService.getOptions(healthCode);
            String externalId = lookup.getString(EXTERNAL_IDENTIFIER);
            if (externalId != null) {
                externalIdService.unassignExternalId(study, externalId, healthCode);    
            }
            optionsService.deleteAllParticipantOptions(healthCode);
            accountDao.deleteAccount(study, account.getId());
        }
    }
}
