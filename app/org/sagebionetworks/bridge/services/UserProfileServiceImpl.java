package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.stormpath.StormpathAccountIterator;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
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
    public List<StudyParticipant> getStudyParticipants(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");

        List<StudyParticipant> participants = Lists.newArrayList();

        // We must iterate over every account in the application, not just the study directory, in case 
        // a user signed up with a different study. There is no way to search/query for specific custom 
        // data, we must iterate over all accounts. Will this exceed 30 seconds eventually? It could.
        // One solution would be to do this in a worker thread and email the results to the researcher.
        String consentKey = study.getIdentifier() + BridgeConstants.CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX; 
        Application app = StormpathFactory.getStormpathApplication(StormpathFactory.getStormpathClient());
        
        StormpathAccountIterator iterator = new StormpathAccountIterator(app, 250);
        for (List<Account> page : iterator) {
            for (Account account : page) {
                if (account.getCustomData().containsKey(consentKey)) {
                    UserProfile profile = profileFromAccount(account);
                    StudyParticipant p = new StudyParticipant();
                    p.setFirstName(profile.getFirstName());
                    p.setLastName(profile.getLastName());
                    p.setEmail(profile.getEmail());
                    p.setPhone(profile.getPhone());
                    participants.add(p);
                }
            }
        }
        return participants;
    }

    private UserProfile profileFromAccount(Account account) {
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
