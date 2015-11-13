package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;

import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FPHSService {
    
    private FPHSExternalIdentifierDao fphsDao;
    private ParticipantOptionsService optionsService;

    @Autowired
    public final void setFPHSExternalIdentifierDao(FPHSExternalIdentifierDao dao) {
        this.fphsDao = dao;
    }
    @Autowired
    public final void setParticipantOptionsService(ParticipantOptionsService options) {
        this.optionsService = options;
    }
    
    public void verifyExternalIdentifier(ExternalIdentifier externalId) throws Exception {
        checkNotNull(externalId);
        
        if (isBlank(externalId.getIdentifier())) {
            throw new InvalidEntityException(externalId);
        }
        // Throws exception if not verified
        fphsDao.verifyExternalId(externalId);
    }
    public void registerExternalIdentifier(StudyIdentifier studyId, String healthCode, ExternalIdentifier externalId) throws Exception {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(externalId);
        
        if (isBlank(externalId.getIdentifier())) {
            throw new InvalidEntityException(externalId);
        }
        fphsDao.registerExternalId(externalId);
        try {
            optionsService.setExternalIdentifier(studyId, healthCode, externalId.getIdentifier());
        } catch(Exception e) {
            fphsDao.unregisterExternalId(externalId);
            throw e;
        }
    }
    
    /**
     * Get all FPHS identifiers along with information about which ones have been used to register.
     * 
     * @return
     * @throws Exception
     */
    public List<FPHSExternalIdentifier> getExternalIdentifiers() throws Exception {
        return fphsDao.getExternalIds();
    }
    
    /**
     * Add new external identifiers to the database. This will not overwrite the registration status of existing
     * external IDs.
     * 
     * @param externalIds
     * @throws Exception
     */
    public void addExternalIdentifiers(List<FPHSExternalIdentifier> externalIds) throws Exception {
        checkNotNull(externalIds);
        fphsDao.addExternalIds(externalIds);
    }
}
