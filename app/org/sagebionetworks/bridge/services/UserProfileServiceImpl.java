package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserProfileServiceImpl implements UserProfileService {
    
    private AccountDao accountDao;
    
    private ExecutorService executorService;
    
    private SendMailService sendMailService;

    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Resource(name = "asyncExecutorService")
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
    
    @Autowired
    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Override
    public UserProfile getProfile(Study study, String email) {
        Account account = accountDao.getAccount(study, email);
        return profileFromAccount(account);
    }
    
    @Override
    public User updateProfile(Study study, User user, UserProfile profile) {
        Account account = accountDao.getAccount(study, user.getEmail());
        account.setFirstName(profile.getFirstName());
        account.setLastName(profile.getLastName());
        account.setPhone(profile.getPhone());
        accountDao.updateAccount(study, account);
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        return user;
    }
    
    @Override
    public void sendStudyParticipantRoster(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        // Verify there is an email address to notify
        if (isBlank(study.getConsentNotificationEmail())) {
            throw new BridgeServiceException("Participant roster cannot be sent because no consent notification contact email exists for this study.");
        }
        Iterator<Account> accounts = accountDao.getStudyAccounts(study);
        executorService.submit(new ParticipantRosterGenerator(accounts, study, sendMailService));
    }

    @Override
    public UserProfile profileFromAccount(Account account) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(account.getFirstName());
        profile.setLastName(account.getLastName());
        profile.setUsername(account.getUsername());
        profile.setEmail(account.getEmail());
        profile.setPhone(account.getPhone());
        return profile;
    }

}
