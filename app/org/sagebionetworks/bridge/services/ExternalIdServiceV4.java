package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;

import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.ExternalIdValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

@Component
public class ExternalIdServiceV4 {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private ExternalIdDao externalIdDao;
    
    private ExternalIdValidator validator;
    
    @Autowired
    final void setExternalIdDao(ExternalIdDao externalIdDao) {
        this.externalIdDao = externalIdDao;
    }
    
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.validator = new ExternalIdValidator(substudyService);
    }
    
    public ExternalIdentifier getExternalId(StudyIdentifier studyId, String externalId) {
        ExternalIdentifier identifier = externalIdDao.getExternalId(studyId, externalId);
        
        if (identifier == null ||  BridgeUtils.filterForSubstudy(identifier) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        return identifier;
    }
    
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(
            String offsetKey, Integer pageSize, String idFilter, Boolean assignmentFilter) {
        
        if (pageSize == null) {
            pageSize = BridgeConstants.API_DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }

        StudyIdentifier studyId = BridgeUtils.getRequestContext().getCallerStudyIdentifier();
        return externalIdDao.getExternalIds(studyId, offsetKey, pageSize, idFilter, assignmentFilter);
    }
    
    public void createExternalIdentifier(ExternalIdentifier externalIdentifier) {
        checkNotNull(externalIdentifier);
        
        StudyIdentifier studyId = BridgeUtils.getRequestContext().getCallerStudyIdentifier();
        externalIdentifier.setStudyId(studyId.getIdentifier());
        
        // In this one  case, we can default the value for the caller and avoid an error. Any other situation
        // is going to generate a validation error
        Set<String> callerSubstudyIds = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (externalIdentifier.getSubstudyId() == null && callerSubstudyIds.size() == 1) {
            externalIdentifier.setSubstudyId( Iterables.getFirst(callerSubstudyIds, null));
        }
        
        Validate.entityThrowingException(validator, externalIdentifier);
        
        // Note that this external ID must be unique across the whole study, not just a substudy, or else
        // it cannot be used to identify the substudy a new account should be assigned to.
        ExternalIdentifier existing = externalIdDao.getExternalId(studyId, externalIdentifier.getIdentifier());
        if (existing != null) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", externalIdentifier.getIdentifier());
        }
        externalIdDao.createExternalIdentifier(externalIdentifier);
    }
    
    public void deleteExternalIdentifier(ExternalIdentifier externalIdentifier) {
        checkNotNull(externalIdentifier);
        
        externalIdDao.deleteExternalIdentifier(externalIdentifier);
    }
    
    public void assignExternalId(Account account, String externalIdentifier) {
        checkNotNull(account);
        checkNotNull(account.getStudyId());
        checkNotNull(account.getHealthCode());
        
        if (externalIdentifier != null) {
            externalIdDao.assignExternalId(account, externalIdentifier);
        }
    }
    
    public void unassignExternalId(Account account, String externalIdentifier) {
        checkNotNull(account);
        checkNotNull(account.getStudyId());
        checkNotNull(account.getHealthCode());
        
        if (externalIdentifier != null) {
            externalIdDao.unassignExternalId(account, externalIdentifier);
        }
    }
    
    public void deleteExternalId(Study study, String externalIdentifier) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));
        
        if (study.isExternalIdValidationEnabled()) {
            throw new BadRequestException("Cannot delete IDs while externalId validation is enabled for this study.");
        }
        ExternalIdentifier existing = externalIdDao.getExternalId(study.getStudyIdentifier(), externalIdentifier);
        if (existing == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        if (BridgeUtils.filterForSubstudy(existing) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        externalIdDao.deleteExternalIdentifier(existing);
    }
}
