package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
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
public class ExternalIdService {
    
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
        checkNotNull(studyId);
        checkNotNull(externalId);
        
        ExternalIdentifier existing = externalIdDao.getExternalId(studyId, externalId);
        
        if (existing == null ||  BridgeUtils.filterForSubstudy(existing) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        return existing;
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
    
    public void createExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        StudyIdentifier studyId = BridgeUtils.getRequestContext().getCallerStudyIdentifier();
        externalId.setStudyId(studyId.getIdentifier());
        
        // In this one  case, we can default the value for the caller and avoid an error. Any other situation
        // is going to generate a validation error
        Set<String> callerSubstudyIds = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (externalId.getSubstudyId() == null && callerSubstudyIds.size() == 1) {
            externalId.setSubstudyId( Iterables.getFirst(callerSubstudyIds, null) );
        }
        
        Validate.entityThrowingException(validator, externalId);
        
        // Note that this external ID must be unique across the whole study, not just a substudy, or else
        // it cannot be used to identify the substudy, and that's a significant purpose behind the 
        // association of the two
        ExternalIdentifier existing = externalIdDao.getExternalId(studyId, externalId.getIdentifier());
        if (existing != null) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier",
                    externalId.getIdentifier());
        }
        externalIdDao.createExternalIdentifier(externalId);
    }
    
    public void deleteExternalIdPermanently(Study study, ExternalIdentifier externalId) {
        checkNotNull(study);
        checkNotNull(externalId);
        
        if (study.isExternalIdValidationEnabled()) {
            throw new BadRequestException("Cannot delete IDs while externalId validation is enabled for this study.");
        }
        
        ExternalIdentifier existing = externalIdDao.getExternalId(study.getStudyIdentifier(), externalId.getIdentifier());
        if (existing == null ||  BridgeUtils.filterForSubstudy(existing) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        externalIdDao.deleteExternalIdentifier(externalId);
    }
    
    public void assignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkNotNull(account.getStudyId());
        checkNotNull(account.getHealthCode());
        
        if (externalId != null) {
            externalIdDao.assignExternalId(account, externalId);
        }
    }
    
    public void unassignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkNotNull(account.getStudyId());
        checkNotNull(account.getHealthCode());
        
        if (externalId != null) {
            externalIdDao.unassignExternalId(account, externalId);
        }
    }
    
}
