package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserProfileService {
    
    private AccountDao accountDao;
    
    private ExecutorService executorService;
    
    private SendMailService sendMailService;
    
    private HealthCodeService healthCodeService;
    
    private ParticipantOptionsService optionsService;
    
    private SubpopulationService subpopService;

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Resource(name = "asyncExecutorService")
    final void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
    
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }
    
    @Autowired
    final void setHealthCodeSerivce(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    
    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    public UserProfile getProfile(Study study, String email) {
        Account account = accountDao.getAccount(study, email);
        return profileFromAccount(study, account);
    }
    
    public User updateProfile(Study study, User user, UserProfile profile) {
        Account account = accountDao.getAccount(study, user.getEmail());
        account.setFirstName(profile.getFirstName());
        account.setLastName(profile.getLastName());
        for(String attribute : study.getUserProfileAttributes()) {
            String value = profile.getAttribute(attribute);
            account.setAttribute(attribute, value);
        }
        accountDao.updateAccount(study, account);
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        return user;
    }
    
    public void sendStudyParticipantRoster(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        // Verify there is an email address to notify
        if (isBlank(study.getConsentNotificationEmail())) {
            throw new BridgeServiceException("Participant roster cannot be sent because no consent notification contact email exists for this study.");
        }
        // Getting the study accounts would be the time-consuming activity here, but in fact only the 
        // first page is retrieved and the rest is retrieved in the new thread during page iteration.
        // Not clear though from this code, and could change with the implementation.
        Iterator<Account> accounts = accountDao.getStudyAccounts(study);
        executorService.submit(new ParticipantRosterGenerator(
            accounts, study, sendMailService, healthCodeService, optionsService, subpopService));
    }

    public UserProfile profileFromAccount(Study study, Account account) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(account.getFirstName());
        profile.setLastName(account.getLastName());
        profile.setEmail(account.getEmail());
        for (String attribute : study.getUserProfileAttributes()) {
            profile.setAttribute(attribute, account.getAttribute(attribute));
        }
        return profile;
    }

}
