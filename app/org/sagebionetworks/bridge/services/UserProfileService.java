package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserProfileService {
    
    private AccountDao accountDao;
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    public UserProfile getProfile(Study study, String id) {
        Account account = accountDao.getAccount(study, id);
        return profileFromAccount(study, account);
    }
    
    public User updateProfile(Study study, User user, UserProfile profile) {
        Account account = accountDao.getAccount(study, user.getId());
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
