package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.models.DynamoPagedResourceList;
import org.sagebionetworks.bridge.models.studies.Study;

/**
 * Service for managing external IDs. These methods can be called whether or not strict validation of IDs is enabled. 
 * If it's enabled, reservation and assignment will work as expected, otherwise these silently do nothing. The identifier 
 * will be saved in the ParticipantOptions table.
 */
@Component
public class ExternalIdService {
    
    private ExternalIdDao externalIdDao;
    
    private ParticipantOptionsService optionsService;
    
    @Autowired
    final void setExternalIdDao(ExternalIdDao externalIdDao) {
        this.externalIdDao = externalIdDao;
    }
    
    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    public DynamoPagedResourceList<String> getExternalIds(Study study, String offsetKey, Integer pageSize, 
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
        
        externalIdDao.addExternalIds(study.getStudyIdentifier(), externalIdentifiers);
    }
    
    public void reserveExternalId(Study study, String externalIdentifier) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));

        if (study.isExternalIdValidationEnabled()) {
            externalIdDao.reserveExternalId(study.getStudyIdentifier(), externalIdentifier);
        }
    }
    
    public void assignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));
        checkArgument(isNotBlank(healthCode));
        
        if (study.isExternalIdValidationEnabled()) {
            externalIdDao.assignExternalId(study.getStudyIdentifier(), externalIdentifier, healthCode);
        }
        optionsService.setString(study.getStudyIdentifier(), healthCode, EXTERNAL_IDENTIFIER, externalIdentifier);
    }
    
    public void unassignExternalId(Study study, String externalIdentifier, String healthCode) {
        checkNotNull(study);
        checkArgument(isNotBlank(externalIdentifier));
        checkArgument(isNotBlank(healthCode));
        
        externalIdDao.unassignExternalId(study.getStudyIdentifier(), externalIdentifier);
        optionsService.setString(study.getStudyIdentifier(), healthCode, EXTERNAL_IDENTIFIER, null);
    }

    public void deleteExternalIds(Study study, List<String> externalIdentifiers) {
        checkNotNull(study);
        checkNotNull(externalIdentifiers);
        
        externalIdDao.deleteExternalIds(study.getStudyIdentifier(), externalIdentifiers);    
    }
}
