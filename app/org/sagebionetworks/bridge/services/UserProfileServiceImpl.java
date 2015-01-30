package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.sagebionetworks.bridge.validators.Validate;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.directory.CustomData;

public class UserProfileServiceImpl implements UserProfileService {
    
    private AuthenticationService authService;
    
    private AesGcmEncryptor healthCodeEncryptor;
    
    private ExecutorService executorService;
    
    private SendMailService sendMailService;

    public void setAuthenticationService(AuthenticationService authService) {
        this.authService = authService;
    }
    
    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }
    
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
    
    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Override
    public UserProfile getProfile(String email) {
        Account account = authService.getAccount(email);
        return profileFromAccount(account);
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
            data.put(BridgeConstants.PHONE_ATTRIBUTE, encryptedPhone);
        }
        account.save();
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        return user;
    }
    
    @Override
    public void sendStudyParticipantRoster(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        // Verify there is an email address to notify
        if (StringUtils.isBlank(study.getConsentNotificationEmail())) {
            throw new BridgeServiceException("Participant roster cannot be sent because no consent notification contact email exists for this study.");
        }
        Application app = StormpathFactory.getStormpathApplication(StormpathFactory.getStormpathClient());
        executorService.submit(new ParticipantRosterGenerator(app, study, this, sendMailService));
    }

    @Override
    public UserProfile profileFromAccount(Account account) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(removeEmpty(account.getGivenName()));
        profile.setLastName(removeEmpty(account.getSurname()));
        profile.setUsername(account.getUsername());
        profile.setEmail(account.getEmail());
        String encryptedPhone = (String)account.getCustomData().get(BridgeConstants.PHONE_ATTRIBUTE);
        if (encryptedPhone != null) {
            profile.setPhone(healthCodeEncryptor.decrypt(encryptedPhone));
        }
        return profile;
    }
    
    private String removeEmpty(String s) {
        if (StringUtils.isBlank(s) || s.equalsIgnoreCase("<EMPTY>")) {
            return null;
        } else {
            return s;
        }
    }

}
