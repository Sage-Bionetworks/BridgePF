package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
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
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
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
    
    public ExternalIdentifier getExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkNotNull(externalId);
        
        return externalIdDao.getExternalId(studyId, externalId);
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
    
    public void assignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkNotNull(healthCode);
        
        if (externalIdentifier != null) {
            externalIdDao.assignExternalId(study.getStudyIdentifier(), externalIdentifier, healthCode);
        }
    }
    
    public void unassignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));
        checkArgument(isNotBlank(healthCode));
        
        externalIdDao.unassignExternalId(study.getStudyIdentifier(), externalIdentifier);
    }

    public void deleteExternalIds(Study study, List<String> externalIdentifiers) {
        checkNotNull(study);
        checkNotNull(externalIdentifiers);
        
        // This will fail if validation is enabled and any of these external identifiers are in use
        // by a participant. That will need to be resolved either by disabling validation or deleting
        // the account.
        if (study.isExternalIdValidationEnabled()) {
            for(String externalId : externalIdentifiers) {
                AccountId accountId = AccountId.forExternalId(study.getIdentifier(), externalId);
                Account account = accountDao.getAccount(accountId);
                if (account != null) {
                    throw new BadRequestException("Cannot delete externalId '" + externalId
                            + "' because it is in use and validation is enabled.");
                }
            }
        }
        externalIdDao.deleteExternalIds(study.getStudyIdentifier(), externalIdentifiers);    
    }
}
