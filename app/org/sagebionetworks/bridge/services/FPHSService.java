package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;

import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
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
    final void setFPHSExternalIdentifierDao(FPHSExternalIdentifierDao dao) {
        this.fphsDao = dao;
    }
    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService options) {
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
        optionsService.setString(studyId, healthCode, EXTERNAL_IDENTIFIER, externalId.getIdentifier());
        try {
            fphsDao.registerExternalId(externalId);    
        } catch(Exception e) {
            optionsService.deleteOption(healthCode, ParticipantOption.EXTERNAL_IDENTIFIER);
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
