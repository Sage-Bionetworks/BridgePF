package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private AccountDao accountDao;
    private HealthCodeService healthCodeService;

    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    
    @Override
    public HealthId createAndSaveHealthCode(Study study, Account account) {
        checkNotNull(study);
        checkNotNull(account);
        
        final HealthId healthId = healthCodeService.create(study.getStudyIdentifier());
        account.setHealthId(healthId.getId());
        accountDao.updateAccount(study, account);
        return healthId;
    }

    @Override
    public HealthId getHealthCode(StudyIdentifier studyIdentifier, Account account) {
        checkNotNull(studyIdentifier);
        checkNotNull(account);
        
        String healthId = account.getHealthId();
        if (healthId == null) {
            return null;
        }
        return (getHealthId(account.getHealthId()));
    }

    @Override
    public void putConsentSignature(Study study, Account account, ConsentSignature consentSignature) {
        
        account.setConsentSignature(consentSignature);
        accountDao.updateAccount(study, account);
    }

    @Override
    public ConsentSignature getConsentSignature(Account account) {
        
        ConsentSignature sig = account.getConsentSignature();
        if (sig == null) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        return sig;
    }

    @Override
    public void removeConsentSignature(Study study, Account account) {
        account.setConsentSignature(null);
        accountDao.updateAccount(study, account);
    }

    private HealthId getHealthId(final String healthId) {
        final String healthCode = healthCodeService.getHealthCode(healthId);
        return new HealthId() {
            @Override
            public String getId() {
                return healthId;
            }
            @Override
            public String getCode() {
                return healthCode;
            }
        };
    }

}
