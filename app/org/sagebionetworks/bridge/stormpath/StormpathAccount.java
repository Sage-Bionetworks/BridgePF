package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Encryption
public class StormpathAccount implements Account {

    // TODO: REMOVEME
    public class StudyIdentifier {
        public String getIdentifier() {
            return null;
        }
        public String getResearcherRole() {
            return null;
        }
    }
    
    private static Logger logger = LoggerFactory.getLogger(StormpathAccount.class);
    
    private static final String PHONE_ATTRIBUTE = "phone";
    
    private StudyIdentifier studyIdentifier;
    private AesGcmEncryptor encryptor;
    private AccountEncryptionService accountEncryptionService;
    private com.stormpath.sdk.account.Account acct;
    
    StormpathAccount(StudyIdentifier studyIdentifier, com.stormpath.sdk.account.Account acct, AesGcmEncryptor healthCodeEncryptor, AccountEncryptionService accountEncryptionService) {
        this.studyIdentifier = studyIdentifier;
        this.acct = acct;
        this.encryptor = healthCodeEncryptor;
        this.accountEncryptionService = accountEncryptionService;
    }
    
    @Override
    public String getUsername() {
        return acct.getUsername();
    }

    @Override
    public void setUsername(String username) {
        acct.setUsername(username);
    }

    @Override
    public String getFirstName() {
        return acct.getGivenName();
    }

    @Override
    public void setFirstName(String firstName) {
        acct.setGivenName(firstName);
    }

    @Override
    public String getLastName() {
        return acct.getSurname();
    }

    @Override
    public void setLastName(String lastName) {
        acct.setSurname(lastName);
    }

    @Override
    public String getPhone() {
        String encryptedPhone = (String)acct.getCustomData().get(PHONE_ATTRIBUTE);
        if (encryptedPhone != null) {
            return encryptor.decrypt(encryptedPhone);
        }
        return null;
    }

    @Override
    public void setPhone(String phone) {
        acct.getCustomData().put(PHONE_ATTRIBUTE, encryptor.encrypt(phone));
    }

    @Override
    public String getEmail() {
        return acct.getEmail();
    }

    @Override
    public void setEmail(String email) {
        acct.setEmail(email);
    }

    @Override
    public String getHealthCode() {
        Study study = new DynamoStudy();
        study.setIdentifier(studyIdentifier.getIdentifier());
        // At some point we want to flip this so the account encryption service does not know about the 
        // internals of the Stormpath code. But not yet.
        HealthId healthId = accountEncryptionService.getHealthCode(study, acct);
        if (healthId == null) {
            healthId = accountEncryptionService.createAndSaveHealthCode(study, acct);
        }
        String healthCode = healthId.getCode();
        if (healthCode == null) {
            healthId = accountEncryptionService.createAndSaveHealthCode(study, acct);
            logger.error("Health code re-created for account " + account.getEmail() + " in study " + study.getName());
            healthCode = healthId.getCode();
        }
        checkNotNull(healthCode);
        return healthCode;
    }
    
    
}
