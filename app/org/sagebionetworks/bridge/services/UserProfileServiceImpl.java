package org.sagebionetworks.bridge.services;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class UserProfileServiceImpl implements UserProfileService {

    private AuthenticationService authService;
    
    private AesGcmEncryptor healthCodeEncryptor;

    public void setAuthenticationService(AuthenticationService authService) {
        this.authService = authService;
    }
    
    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    @Override
    public UserProfile getProfile(String email) {
        Account account = authService.getAccount(email);

        UserProfile profile = new UserProfile();
        profile.setFirstName(removeEmpty(account.getGivenName()));
        profile.setLastName(removeEmpty(account.getSurname()));
        profile.setUsername(removeEmpty(account.getUsername()));
        profile.setEmail(removeEmpty(account.getEmail()));
        
        String encryptedPhone = (String)account.getCustomData().get("phone");
        if (encryptedPhone != null) {
            profile.setPhone(healthCodeEncryptor.decrypt(encryptedPhone));
        }
        return profile;
    }
    
    @Override
    public User updateProfile(User user, UserProfile profile) {
        Account account = authService.getAccount(user.getEmail());
        account.setGivenName(profile.getFirstNameWithEmptyString());
        account.setSurname(profile.getLastNameWithEmptyString());
        if (profile.getPhone() != null) {
            CustomData data = account.getCustomData();
            // encrypt
            String encryptedPhone = healthCodeEncryptor.encrypt(profile.getPhone());
            data.put("phone", encryptedPhone);
        }
        account.save();
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        return user;
    }
    
    private String removeEmpty(String s) {
        if (StringUtils.isBlank(s) || s.equalsIgnoreCase("<EMPTY>")) {
            return "";
        } else {
            return s;
        }
    }

}
