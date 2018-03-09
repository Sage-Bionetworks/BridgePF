package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.ExternalIdsValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Service for managing external IDs. These methods can be called whether or not strict validation of IDs is enabled. 
 * If it's enabled, reservation and assignment will work as expected, otherwise these silently do nothing. The identifier 
 * will be saved in the ParticipantOptions table.
 */
@Component
public class ExternalIdService {
    
    private ExternalIdDao externalIdDao;
    
    private AccountDao accountDao;
    
    private ExternalIdsValidator validator;
    
    @Autowired
    final void setExternalIdDao(ExternalIdDao externalIdDao) {
        this.externalIdDao = externalIdDao;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setConfig(Config config) {
        validator = new ExternalIdsValidator(config.getInt(ExternalIdDao.CONFIG_KEY_ADD_LIMIT));
    }
    
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(Study study, String offsetKey, Integer pageSize,
            String idFilter, Boolean assignmentFilter) {
        checkNotNull(study);
        if (pageSize == null) {
            pageSize = BridgeConstants.API_DEFAULT_PAGE_SIZE;
        }
        return externalIdDao.getExternalIds(study.getStudyIdentifier(), offsetKey, pageSize, 
                idFilter, assignmentFilter);
    }
    
    public void addExternalIds(Study study, List<String> externalIdentifiers) {
        checkNotNull(study);
        checkNotNull(externalIdentifiers);
        
        Validate.entityThrowingException(validator, new ExternalIdsValidator.ExternalIdList(externalIdentifiers));
        
        externalIdDao.addExternalIds(study.getStudyIdentifier(), externalIdentifiers);
    }
    
    public void reserveExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkNotNull(healthCode);
        
        if (study.isExternalIdValidationEnabled() && isNotBlank(externalIdentifier)) {
            externalIdDao.reserveExternalId(study.getStudyIdentifier(), externalIdentifier);    
        }
    }
    
    public void assignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkNotNull(healthCode);
        
        AccountId accountId = AccountId.forHealthCode(study.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            if (study.isExternalIdValidationEnabled()) {
                String existingExternalId = account.getExternalId();

                if (isBlank(existingExternalId)) {
                    if (isNotBlank(externalIdentifier)) {
                        externalIdDao.assignExternalId(study.getStudyIdentifier(), externalIdentifier, healthCode);
                    }
                } else if (!existingExternalId.equals(externalIdentifier)) {
                    throw new BadRequestException(
                            "External ID cannot be changed or removed after assignment.");
                }
            }
            account.setExternalId(externalIdentifier);
            accountDao.updateAccount(account, false);
        }
    }
    
    public void unassignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));
        checkArgument(isNotBlank(healthCode));
        
        externalIdDao.unassignExternalId(study.getStudyIdentifier(), externalIdentifier);
        // NOTE: This is called before the account itself is deleted, so this is not necessary. Leave it in
        // until we refactor the handling of external IDs.
        accountDao.editAccount(study.getStudyIdentifier(), healthCode, account -> account.setExternalId(null));
    }

    public void deleteExternalIds(Study study, List<String> externalIdentifiers) {
        checkNotNull(study);
        checkNotNull(externalIdentifiers);
        
        if (study.isExternalIdValidationEnabled()) {
            throw new BadRequestException("Cannot delete IDs while externalId validation is enabled for this study.");
        }
        externalIdDao.deleteExternalIds(study.getStudyIdentifier(), externalIdentifiers);    
    }
}
