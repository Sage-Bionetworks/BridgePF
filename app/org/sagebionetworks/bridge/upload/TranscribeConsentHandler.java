package org.sagebionetworks.bridge.upload;

import static org.sagebionetworks.bridge.BridgeUtils.mapSubstudyMemberships;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private AccountDao accountDao;

    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        HealthDataRecord record = context.getHealthDataRecord();
        
        AccountId accountId = AccountId.forHealthCode(context.getStudy().getIdentifier(), context.getHealthCode());
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            // write user info to health data record
            record.setUserSharingScope(account.getSharingScope());
            record.setUserExternalId(account.getExternalId());
            record.setUserDataGroups(account.getDataGroups());
            record.setUserSubstudyMemberships( mapSubstudyMemberships(account) );
        } else {
            // default sharing to NO_SHARING
            record.setUserSharingScope(SharingScope.NO_SHARING);
        }
    }
}
